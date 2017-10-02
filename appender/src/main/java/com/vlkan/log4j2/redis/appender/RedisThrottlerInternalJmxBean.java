package com.vlkan.log4j2.redis.appender;

import com.google.common.base.MoreObjects;

import java.util.concurrent.atomic.AtomicLong;

public class RedisThrottlerInternalJmxBean implements RedisThrottlerJmxBean {

    private final AtomicLong totalEventCount = new AtomicLong();

    private final AtomicLong rateLimitFailureCount = new AtomicLong(0);

    private final AtomicLong unavailableBufferSpaceFailureCount = new AtomicLong(0);

    private final AtomicLong redisPushFailureCount = new AtomicLong(0);

    private final AtomicLong redisPushSuccessCount = new AtomicLong(0);

    public RedisThrottlerInternalJmxBean() {
        // Do nothing.
    }

    @Override
    public long getTotalEventCount() {
        return totalEventCount.get();
    }

    @Override
    public long setTotalEventCount(long count) {
        totalEventCount.set(count);
        return count;
    }

    @Override
    public void incrementTotalEventCount(long count) {
        totalEventCount.addAndGet(count);
    }

    @Override
    public long getRateLimitFailureCount() {
        return rateLimitFailureCount.get();
    }

    @Override
    public long setRateLimitFailureCount(long count) {
        rateLimitFailureCount.set(count);
        return count;
    }

    @Override
    public void incrementRateLimitFailureCount(long increment) {
        rateLimitFailureCount.addAndGet(increment);
    }

    @Override
    public long getUnavailableBufferSpaceFailureCount() {
        return unavailableBufferSpaceFailureCount.get();
    }

    @Override
    public long setUnavailableBufferSpaceFailureCount(long count) {
        unavailableBufferSpaceFailureCount.set(count);
        return count;
    }

    @Override
    public void incrementUnavailableBufferSpaceFailureCount(long increment) {
        unavailableBufferSpaceFailureCount.addAndGet(increment);
    }

    @Override
    public long getRedisPushFailureCount() {
        return redisPushFailureCount.get();
    }

    @Override
    public long setRedisPushFailureCount(int count) {
        redisPushFailureCount.set(count);
        return count;
    }

    @Override
    public void incrementRedisPushFailureCount(int increment) {
        redisPushFailureCount.addAndGet(increment);
    }

    @Override
    public long getRedisPushSuccessCount() {
        return redisPushSuccessCount.get();
    }

    @Override
    public long setRedisPushSuccessCount(int count) {
        redisPushSuccessCount.set(count);
        return count;
    }

    @Override
    public void incrementRedisPushSuccessCount(int increment) {
        redisPushSuccessCount.addAndGet(increment);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("totalEventCount", totalEventCount.get())
                .add("rateLimitFailureCount", rateLimitFailureCount.get())
                .add("unavailableBufferSpaceFailureCount", unavailableBufferSpaceFailureCount.get())
                .add("redisPushFailureCount", redisPushFailureCount.get())
                .add("redisPushSuccessCount", redisPushSuccessCount.get())
                .toString();
    }

}
