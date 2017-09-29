package com.vlkan.log4j2.redis.appender;

import com.google.common.util.concurrent.RateLimiter;

import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class RedisThrottler implements AutoCloseable {

    private final RedisThrottlerConfig config;

    private final RedisThrottlerReceiver receiver;

    private final boolean ignoreExceptions;

    private final BlockingQueue<byte[]> buffer;

    private final byte[][] batch;

    private final Thread flushTrigger;

    private final RateLimiter rateLimiter;

    private final DebugLogger logger;

    private final AtomicReference<Throwable> lastThrown = new AtomicReference<>(null);

    RedisThrottler(RedisThrottlerConfig config, RedisThrottlerReceiver receiver, boolean ignoreExceptions, boolean debugEnabled) {
        this.config = config;
        this.receiver = receiver;
        this.ignoreExceptions = ignoreExceptions;
        this.buffer = new ArrayBlockingQueue<>(config.getBufferSize());
        this.batch = new byte[config.getBatchSize()][];
        this.flushTrigger = createFlushTrigger();
        this.rateLimiter = config.getMaxEventCountPerSecond() > 0 ? RateLimiter.create(config.getMaxEventCountPerSecond()) : null;
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
        try {
            logger.debug("pushing %d events", events.length);
            receiver.consumeThrottledEvents(events);
        } catch (Throwable thrown) {
            if (logger.isEnabled()) {
                logger.debug("push failure: %s", thrown.getMessage());
                thrown.printStackTrace();
            }
            lastThrown.set(thrown);
        }
    }

    public void push(byte[] event) {

        Throwable thrown = lastThrown.getAndSet(null);
        if (thrown != null) {
            tryThrow(thrown.getMessage());
            return;
        }

        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            tryThrow("failed acquiring rate limiter token");
            return;
        }

        if (!buffer.offer(event)) {
            tryThrow("failed enqueueing");
        }

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

}
