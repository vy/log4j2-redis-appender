package com.vlkan.log4j2.redis.appender;

import com.google.common.util.concurrent.RateLimiter;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class RedisThrottler implements AutoCloseable {

    private final RedisThrottlerConfig config;

    private final RedisAppender appender;

    private final boolean ignoreExceptions;

    private final BlockingQueue<byte[]> buffer;

    private final byte[][] batch;

    private final Thread flushTrigger;

    private final RateLimiter eventPerSecondLimiter;

    private final RateLimiter bytePerSecondLimiter;

    private final DebugLogger logger;

    private final AtomicReference<Throwable> lastThrown = new AtomicReference<>(null);

    private final RedisAppenderStatsCounter statsCounter = new RedisAppenderStatsCounter();

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
        this.eventPerSecondLimiter = config.getMaxEventCountPerSecond() > 0 ? RateLimiter.create(config.getMaxEventCountPerSecond()) : null;
        this.bytePerSecondLimiter = config.getMaxByteCountPerSecond() > 0 ? RateLimiter.create(config.getMaxByteCountPerSecond()) : null;
        this.logger = new DebugLogger(RedisThrottler.class, debugEnabled);
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
            statsCounter.recordPushSuccess(eventCount);
        } catch (Throwable thrown) {
            if (logger.isEnabled()) {
                logger.debug("push failure: %s", thrown.getMessage());
                thrown.printStackTrace();
            }
            lastThrown.set(thrown);
            statsCounter.recordPushFailure(eventCount);
        }
    }

    public void push(byte[] event) {
        statsCounter.recordNewEvent(1);

        Throwable thrown = lastThrown.getAndSet(null);
        if (thrown != null) {
            statsCounter.recordIgnoredEvent(1);
            tryThrow(thrown);
            return;
        }

        if (eventPerSecondLimiter != null && !eventPerSecondLimiter.tryAcquire()) {
            statsCounter.recordEventRateLimitFailure(1);
            tryThrow("failed acquiring event rate limiter token");
            return;
        }

        if (bytePerSecondLimiter != null && !bytePerSecondLimiter.tryAcquire(event.length)) {
            statsCounter.recordByteRateLimitFailure(1);
            tryThrow("failed acquiring byte rate limiter token");
            return;
        }

        if (!buffer.offer(event)) {
            statsCounter.recordUnavailableBufferSpaceFailure(1);
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
        flushTrigger.start();
    }

    @Override
    public synchronized void close() {
        logger.debug("closing");
        flushTrigger.interrupt();
    }

    RedisAppenderStats getStats() {
        return statsCounter;
    }
}
