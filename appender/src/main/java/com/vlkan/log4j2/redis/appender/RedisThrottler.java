/*
 * Copyright 2017-2022 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */
package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.status.StatusLogger;

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

    private static final StatusLogger LOGGER = StatusLogger.getLogger();

    /**
     * Reference count of JMX beans.
     * <p>
     * Certain applications (e.g., Spring Boot) known to reconfigure
     * <code>LoggerContext</code> multiple times. This triggers multiple
     * interleaved start-stop calls causing <code>RedisThrottler</code> to
     * unregister an in-use JMX bean. This map keeps the reference counts to
     * created JMX beans and unregisters them at close if there are no more
     * references.
     */
    private static final Map<ObjectName, Integer> JMX_BEAN_REFERENCE_COUNT_BY_NAME = new HashMap<>();

    private final RedisThrottlerConfig config;

    private final RedisAppender appender;

    private final String logPrefix;

    private final boolean ignoreExceptions;

    private final BlockingQueue<byte[]> buffer;

    private final byte[][] batch;

    private final Thread flushTrigger;

    private final RateLimiter eventRateLimiter;

    private final RateLimiter byteRateLimiter;

    private final RateLimiter errorRateLimiter;

    private final ObjectName jmxBeanName;

    private volatile boolean started = false;

    private volatile RedisThrottlerJmxBean jmxBean = null;

    /**
     * Index pointing to the next empty item of {@link #batch}.
     * <p>
     * This doesn't need to be guarded, since it is only accessed by the {@link #flushTrigger}.
     * </p>
     */
    private int batchIndex = 0;

    private final AtomicReference<Throwable> lastThrownRef = new AtomicReference<>(null);

    RedisThrottler(
            RedisThrottlerConfig config,
            RedisAppender appender,
            boolean ignoreExceptions) {
        this.config = config;
        this.appender = appender;
        this.logPrefix = String.format("[RedisThrottler{%s}]", appender.getName());
        this.ignoreExceptions = ignoreExceptions;
        this.buffer = new ArrayBlockingQueue<>(config.getBufferSize());
        this.batch = new byte[config.getBatchSize()][];
        this.flushTrigger = createFlushTrigger(appender.getName());
        this.eventRateLimiter = config.getMaxEventCountPerSecond() > 0
                ? RateLimiter.ofMaxPermitCountPerSecond(
                        appender.getName() + "-EventRateLimiter",
                        config.getMaxEventCountPerSecond())
                : null;
        this.byteRateLimiter = config.getMaxByteCountPerSecond() > 0
                ? RateLimiter.ofMaxPermitCountPerSecond(
                        appender.getName()  + "-ByteRateLimiter",
                        config.getMaxByteCountPerSecond())
                : null;
        this.errorRateLimiter = config.getMaxErrorCountPerSecond() > 0
                ? RateLimiter.ofMaxPermitCountPerSecond(
                        appender.getName()  + "-ErrorRateLimiter",
                        config.getMaxErrorCountPerSecond())
                : null;
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

    private Thread createFlushTrigger(String appenderName) {
        Thread thread = new Thread(this::flushContinuously);
        thread.setName(appenderName + " Throttler");
        thread.setDaemon(true);
        return thread;
    }

    private void flushContinuously() {

        // Determine the wait period.
        long waitPeriodNanos = Math.multiplyExact(1_000_000L, config.getFlushPeriodMillis());
        if (LOGGER.isInfoEnabled()) {
            String waitPeriod = String.format("%.3fs", waitPeriodNanos * 1e-9);
            LOGGER.info("{} background task has started (waitPeriod={})", logPrefix, waitPeriod);
        }

        // Flush continuously.
        boolean interrupted = false;
        while (started) {
            LOGGER.debug("{} background task is flushing", logPrefix);
            try {
                flush(waitPeriodNanos);
            }
            // Catch the interrupted exception to avoid getting the current thread interrupted.
            // This is needed because further Redis I/O for the leftovers in the buffer might be performed.
            // `interrupted` flag will be restored later on.
            catch (InterruptedException ignored) {
                LOGGER.debug("{} background task is interrupted", logPrefix);
                interrupted = true;
                break;
            }
        }

        // Upon graceful shutdown, flush one last time for any leftovers in the buffer.
        if (started) {
            LOGGER.debug("{} background task is interrupted abruptly, skipping flushing one last time", logPrefix);
        } else {
            LOGGER.debug("{} background task is flushing one last time", logPrefix);
            try {
                flush(0);
            } catch (InterruptedException ignored) {
                LOGGER.debug("{} last run of the background task is interrupted", logPrefix);
                interrupted = true;
            }
        }

        // Restore the `interrupted` flag, if necessary.
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

    }

    private void flush(long waitPeriodNanos) throws InterruptedException {

        // If waiting on the buffer is not allowed, flush events indeed without waiting.
        if (waitPeriodNanos <= 0) {
            for (byte[] event; (event = buffer.poll()) != null;) {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("{} background task has polled: {}", logPrefix, new String(event).trim());
                }
                batch[batchIndex++] = event;
                if (batchIndex == batch.length) {
                    push(batch);
                    batchIndex = 0;
                }
            }
        }

        // Otherwise, wait on the buffer for events to appear.
        else {
            while (waitPeriodNanos > 0) {
                long pollTimeNanos = System.nanoTime();
                byte[] event = buffer.poll(waitPeriodNanos, TimeUnit.NANOSECONDS);
                if (event == null) {
                    break;
                }
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("{} background task has polled: {}", logPrefix, new String(event).trim());
                }
                batch[batchIndex++] = event;
                if (batchIndex == batch.length) {
                    push(batch);
                    batchIndex = 0;
                }
                long pollPeriodNanos = System.nanoTime() - pollTimeNanos;
                waitPeriodNanos -= pollPeriodNanos;
            }
        }

        // Flush the last remaining.
        if (batchIndex > 0) {
            LOGGER.debug("{} background task is pushing last {} events that didn't fit into the batch", logPrefix, batchIndex);
            byte[][] subBatch = Arrays.copyOfRange(batch, 0, batchIndex);
            push(subBatch);
            batchIndex = 0;
        }

    }

    private void push(final byte[][] events) {
        int eventCount = events.length;
        try {
            LOGGER.debug("{} background task is pushing {} events", logPrefix, eventCount);
            appender.consumeThrottledEvents(events);
            jmxBean.incrementRedisPushSuccessCount(eventCount);
        } catch (Exception thrown) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("{} background task push failure", logPrefix, thrown);
                thrown.printStackTrace();
            }
            lastThrownRef.set(thrown);
            jmxBean.incrementRedisPushFailureCount(eventCount);
        }
    }

    RedisThrottlerJmxBean getJmxBean() {
        return jmxBean;
    }

    synchronized void push(byte[] event) {

        if (!started) {
            LOGGER.debug("{} not started yet, ignoring the push request", logPrefix);
            return;
        }

        jmxBean.incrementTotalEventCount(1);

        Throwable lastThrown = lastThrownRef.getAndSet(null);
        if (lastThrown != null) {
            jmxBean.incrementIgnoredEventCount(1);
            tryThrow("failed pushing due to an earlier throttler failure", lastThrown);
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

    @SuppressWarnings("SameParameterValue")
    private void tryThrow(String message, Throwable error) {
        if (errorRateLimiter == null || errorRateLimiter.tryAcquire()) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error(logPrefix + " " + message, error);
            }
            if (!ignoreExceptions)
                throw new RuntimeException(error);
        }
    }

    private void tryThrow(String error) {
        LOGGER.error(logPrefix + " " + error);
        if (!ignoreExceptions)
            throw new RuntimeException(error);
    }

    synchronized void start() {
        if (!started) {
            LOGGER.info("{} starting", logPrefix);
            started = true;
            jmxBean = registerOrGetJmxBean();
            flushTrigger.start();
        }
    }

    private RedisThrottlerJmxBean registerOrGetJmxBean() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            synchronized (JMX_BEAN_REFERENCE_COUNT_BY_NAME) {

                // Get the reference count for the JMX bean.
                Integer jmxBeanReferenceCount = JMX_BEAN_REFERENCE_COUNT_BY_NAME.get(jmxBeanName);
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
                JMX_BEAN_REFERENCE_COUNT_BY_NAME.put(jmxBeanName, jmxBeanReferenceCount + 1);
                return jmxBean;

            }
        } catch (Exception error) {
            String message = String.format("failed accessing the JMX bean (jmxBeanName=%s)", jmxBeanName);
            throw new RuntimeException(message, error);
        }
    }

    @Override
    public synchronized void close() {
        if (started) {
            LOGGER.info("{} closing", logPrefix);
            started = false;
            flushTrigger.interrupt();
            try {
                flushTrigger.join();
            } catch (InterruptedException ignored) {
                LOGGER.debug("{} stop interrupted", logPrefix);
                Thread.currentThread().interrupt();
            }
            unregisterJmxBean();
        }
    }

    private void unregisterJmxBean() {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        synchronized (JMX_BEAN_REFERENCE_COUNT_BY_NAME) {

            // Get the reference count for the JMX bean.
            Integer jmxBeanReferenceCount = JMX_BEAN_REFERENCE_COUNT_BY_NAME.get(jmxBeanName);

            // Check if we have a valid state, that is, jmxBeanReferenceCount > 0.
            if (jmxBeanReferenceCount == null || jmxBeanReferenceCount == 0) {
                LOGGER.warn(
                        "{} failed unregistering the JMX bean (jmxBeanName={}, jmxBeanReferenceCount={})",
                        logPrefix, jmxBeanName, jmxBeanReferenceCount);
            }

            // If there is just a single reference so far, it is safe to unregister the bean.
            else if (jmxBeanReferenceCount == 1) {
                try {
                    mbs.unregisterMBean(jmxBeanName);
                    JMX_BEAN_REFERENCE_COUNT_BY_NAME.remove(jmxBeanName);
                } catch (Exception error) {
                    String message = String.format(
                            "%s failed unregistering the JMX bean (jmxBeanName=%s)",
                            logPrefix, jmxBeanName);
                    LOGGER.error(message, error);
                }
            }

            // Apparently there are more consumers of the bean. Just decrement the reference count.
            else {
                JMX_BEAN_REFERENCE_COUNT_BY_NAME.put(jmxBeanName, jmxBeanReferenceCount - 1);
            }

        }
    }

}
