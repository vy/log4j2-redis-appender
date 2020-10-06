package com.vlkan.log4j2.redis.appender;

import com.vlkan.log4j2.redis.appender.guava.GuavaRateLimiter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class RedisThrottler implements AutoCloseable {

    /**
     * Reference count of JMX beans.
     * <p>
     * Certain applications (e.g., Spring Boot) known to reconfigure <code>LoggerContext</code> multiple times. This
     * triggers multiple interleaved start-stop calls causing <code>RedisThrottler</code> to unregister an in-use
     * JMX bean. <code>jmxBeanReferenceCountByName</code> keeps the reference counts to created JMX beans and
     * unregisters them at close if there are no more references.
     */
    private static final Map<ObjectName, Integer> jmxBeanReferenceCountByName = new HashMap<>();

    private final RedisThrottlerConfig config;

    private final RedisAppender appender;

    private final boolean ignoreExceptions;

    private final BlockingQueue<byte[]> buffer;

    private final byte[][] batch;

    private final Thread flushTrigger;

    private final GuavaRateLimiter eventRateLimiter;

    private final GuavaRateLimiter byteRateLimiter;

    private final DebugLogger logger;

    private final ObjectName jmxBeanName;

    private volatile RedisThrottlerJmxBean jmxBean = null;

    private final AtomicReference<Throwable> lastThrown = new AtomicReference<>(null);

    RedisThrottler(
            RedisThrottlerConfig config,
            RedisAppender appender,
            boolean ignoreExceptions,
            boolean debugEnabled) {
        this.config = config;
        this.appender = appender;
        this.ignoreExceptions = ignoreExceptions;
        this.buffer = new ArrayBlockingQueue<>(config.getBufferSize());
        this.batch = new byte[config.getBatchSize()][];
        this.flushTrigger = createFlushTrigger();
        this.eventRateLimiter = config.getMaxEventCountPerSecond() > 0 ? GuavaRateLimiter.create(config.getMaxEventCountPerSecond()) : null;
        this.byteRateLimiter = config.getMaxByteCountPerSecond() > 0 ? GuavaRateLimiter.create(config.getMaxByteCountPerSecond()) : null;
        this.logger = new DebugLogger(RedisThrottler.class, debugEnabled);
        this.jmxBeanName = createJmxBeanName();
    }

    private ObjectName createJmxBeanName() {
        String beanName = config.getJmxBeanName();
        if (beanName == null) {
            LoggerContext loggerContext = appender.getConfig().getLoggerContext();
            if (loggerContext == null) {
                loggerContext = (LoggerContext) LogManager.getContext(false);
            }
            beanName = String.format(
                    "org.apache.logging.log4j2:type=%s,component=Appenders,name=%s,subtype=RedisThrottler",
                    loggerContext.getName(),
                    appender.getName());
        }
        try {
            return new ObjectName(beanName);
        } catch (MalformedObjectNameException error) {
            String message = String.format("malformed JMX bean name (beanName=%s)", beanName);
            throw new RuntimeException(message, error);
        }
    }

    private Thread createFlushTrigger() {
        Thread thread = new Thread(this::flushContinuously);
        thread.setDaemon(true);
        return thread;
    }

    private void flushContinuously() {
        logger.debug("started");
        do {
            logger.debug("flushing");
            try {
                flush();
            } catch (InterruptedException ignored) {
                logger.debug("interrupted");
                Thread.currentThread().interrupt();
                break;
            }
        } while (true);
    }

    private void flush() throws InterruptedException {

        int batchIndex = 0;
        byte[] event;

        // Flush in batches.
        logger.debug("polling");
        long waitPeriodMillis = config.getFlushPeriodMillis();
        while (waitPeriodMillis > 0) {
            long pollTimeMillis = System.currentTimeMillis();
            event = buffer.poll(waitPeriodMillis, TimeUnit.MILLISECONDS);
            if (event == null) {
                break;
            }
            if (logger.isEnabled()) {
                logger.debug("polled: %s", new String(event));
            }
            batch[batchIndex++] = event;
            if (batchIndex == batch.length) {
                safeConsumeEvents(batch);
                batchIndex = 0;
            }
            long pollPeriodMillis = System.currentTimeMillis() - pollTimeMillis;
            waitPeriodMillis -= pollPeriodMillis;
        }

        // Flush the last remaining.
        if (batchIndex > 0) {
            logger.debug("pushing remaining %d events", batchIndex);
            byte[][] subBatch = Arrays.copyOfRange(batch, 0, batchIndex);
            safeConsumeEvents(subBatch);
        }

    }

    private void safeConsumeEvents(final byte[]... events) {
        int eventCount = events.length;
        try {
            logger.debug("pushing %d events", eventCount);
            appender.consumeThrottledEvents(events);
            jmxBean.incrementRedisPushSuccessCount(eventCount);
        } catch (Throwable thrown) {
            if (logger.isEnabled()) {
                logger.debug("push failure: %s", thrown.getMessage());
                thrown.printStackTrace();
            }
            lastThrown.set(thrown);
            jmxBean.incrementRedisPushFailureCount(eventCount);
        }
    }

    public RedisThrottlerJmxBean getJmxBean() {
        return jmxBean;
    }

    public void push(byte[] event) {

        jmxBean.incrementTotalEventCount(1);

        Throwable thrown = lastThrown.getAndSet(null);
        if (thrown != null) {
            jmxBean.incrementIgnoredEventCount(1);
            tryThrow(thrown);
            return;
        }

        if (eventRateLimiter != null && !eventRateLimiter.tryAcquire()) {
            jmxBean.incrementByteRateLimitFailureCount(1);
            tryThrow("failed acquiring event rate limiter token");
            return;
        }

        if (byteRateLimiter != null && !byteRateLimiter.tryAcquire(event.length)) {
            jmxBean.incrementByteRateLimitFailureCount(1);
            tryThrow("failed acquiring byte rate limiter token");
            return;
        }

        if (!buffer.offer(event)) {
            jmxBean.incrementUnavailableBufferSpaceFailureCount(1);
            tryThrow("failed enqueueing");
        }

    }

    private void tryThrow(Throwable error) {
        logger.debug(error.getMessage(), error);
        if (!ignoreExceptions)
            throw new RuntimeException(error);
    }

    private void tryThrow(String error) {
        logger.debug(error);
        if (!ignoreExceptions)
            throw new RuntimeException(error);
    }

    public synchronized void start() {
        logger.debug("starting");
        jmxBean = registerOrGetJmxBean();
        flushTrigger.start();
    }

    private RedisThrottlerJmxBean registerOrGetJmxBean() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            synchronized (jmxBeanReferenceCountByName) {

                // Get the reference count for the JMX bean.
                Integer jmxBeanReferenceCount = jmxBeanReferenceCountByName.get(jmxBeanName);
                if (jmxBeanReferenceCount == null) {
                    jmxBeanReferenceCount = 0;
                }

                // Create or get the JMX bean.
                RedisThrottlerJmxBean jmxBean;
                try {
                    jmxBean = new RedisThrottlerInternalJmxBean();
                    StandardMBean jmxBeanWrapper = new StandardMBean(jmxBean, RedisThrottlerJmxBean.class);
                    mbs.registerMBean(jmxBeanWrapper, jmxBeanName);
                } catch (InstanceAlreadyExistsException ignored) {
                    jmxBean = JMX.newMBeanProxy(mbs, jmxBeanName, RedisThrottlerJmxBean.class);
                }

                // Increment the reference count and return the JMX bean.
                jmxBeanReferenceCountByName.put(jmxBeanName, jmxBeanReferenceCount + 1);
                return jmxBean;

            }
        } catch (Throwable error) {
            String message = String.format("failed accessing the JMX bean (jmxBeanName=%s)", jmxBeanName);
            throw new RuntimeException(message, error);
        }
    }

    @Override
    public synchronized void close() {
        logger.debug("closing");
        flushTrigger.interrupt();
        unregisterJmxBean();
    }

    private void unregisterJmxBean() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        synchronized (jmxBeanReferenceCountByName) {

            // Get the reference count for the JMX bean.
            Integer jmxBeanReferenceCount = jmxBeanReferenceCountByName.get(jmxBeanName);

            // Check if we have a valid state, that is, jmxBeanReferenceCount > 0.
            if (jmxBeanReferenceCount == null || jmxBeanReferenceCount == 0) {
                logger.debug(
                        "failed unregistering the JMX bean (jmxBeanName=%s, jmxBeanReferenceCount=%s)",
                        jmxBeanName, jmxBeanReferenceCount);
            }

            // If there is just a single reference so far, it is safe to unregister the bean.
            else if (jmxBeanReferenceCount == 1) {
                try {
                    mbs.unregisterMBean(jmxBeanName);
                    jmxBeanReferenceCountByName.remove(jmxBeanName);
                } catch (Throwable error) {
                    logger.debug("failed unregistering the JMX bean (jmxBeanName=%s)", jmxBeanName);
                }
            }

            // Apparently there are more consumers of the bean. Just decrement the reference count.
            else {
                jmxBeanReferenceCountByName.put(jmxBeanName, jmxBeanReferenceCount - 1);
            }

        }
    }

}
