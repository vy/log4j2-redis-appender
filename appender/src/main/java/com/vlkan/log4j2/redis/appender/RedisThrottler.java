package com.vlkan.log4j2.redis.appender;

import com.google.common.util.concurrent.RateLimiter;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.MoreObjects.firstNonNull;

class RedisThrottler implements AutoCloseable {

    private final RedisThrottlerConfig config;

    private final RedisAppender appender;

    private final boolean ignoreExceptions;

    private final BlockingQueue<byte[]> buffer;

    private final byte[][] batch;

    private final Thread flushTrigger;

    private final RateLimiter rateLimiter;

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
        this.rateLimiter = config.getMaxByteCountPerSecond() > 0 ? RateLimiter.create(config.getMaxByteCountPerSecond()) : null;
        this.logger = new DebugLogger(RedisThrottler.class, debugEnabled);
        this.jmxBeanName = createJmxBeanName();
    }

    private ObjectName createJmxBeanName() {
        String beanName = firstNonNull(
                config.getJmxBeanName(),
                String.format(
                        "org.apache.logging.log4j2:type=%s,component=Appenders,name=%s,subtype=RedisThrottler",
                        appender.getConfig().getLoggerContext().getName(),
                        appender.getName()));
        try {
            return new ObjectName(beanName);
        } catch (MalformedObjectNameException error) {
            String message = String.format("malformed JMX bean name (beanName=%s)", beanName);
            throw new RuntimeException(message, error);
        }
    }

    private Thread createFlushTrigger() {
        return new Thread(new Runnable() {
            @Override
            @SuppressWarnings("InfiniteLoopStatement")
            public void run() {
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
        });
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

    public void push(byte[] event) {

        jmxBean.incrementTotalEventCount(1);

        Throwable thrown = lastThrown.getAndSet(null);
        if (thrown != null) {
            tryThrow(thrown);
            return;
        }

        if (rateLimiter != null && !rateLimiter.tryAcquire(event.length)) {
            jmxBean.incrementRateLimitFailureCount(1);
            tryThrow("failed acquiring rate limiter token");
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
            try {
                RedisThrottlerInternalJmxBean jmxBean = new RedisThrottlerInternalJmxBean();
                StandardMBean jmxBeanWrapper = new StandardMBean(jmxBean, RedisThrottlerJmxBean.class);
                mbs.registerMBean(jmxBeanWrapper, jmxBeanName);
                return jmxBean;
            } catch (InstanceAlreadyExistsException ignored) {
                return JMX.newMBeanProxy(mbs, jmxBeanName, RedisThrottlerJmxBean.class);
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
        try {
            mbs.unregisterMBean(jmxBeanName);
        } catch (Throwable error) {
            logger.debug("failed unregistering the JMX bean (jmxBeanName=%s)", jmxBeanName);
        }
    }

}
