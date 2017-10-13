package com.vlkan.log4j2.redis.appender;

import com.google.common.base.MoreObjects;

import java.util.concurrent.atomic.AtomicLong;

public class RedisThrottlerInternalJmxBean implements RedisThrottlerJmxBean {

    private final AtomicLong totalEventCount = new AtomicLong(0);

    private final AtomicLong ignoredEventCount = new AtomicLong(0);

    private final AtomicLong eventRateLimitFailureCount = new AtomicLong(0);

    private final AtomicLong byteRateLimitFailureCount = new AtomicLong(0);

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
    public long getIgnoredEventCount() {
        return ignoredEventCount.get();
    }

    @Override
    public long setIgnoredEventCount(long count) {
        ignoredEventCount.set(count);
        return count;
    }

    @Override
    public void incrementIgnoredEventCount(long count) {
        ignoredEventCount.addAndGet(count);
    }

    @Override
    public long getEventRateLimitFailureCount() {
        return eventRateLimitFailureCount.get();
    }

    @Override
    public long setEventRateLimitFailureCount(long count) {
        eventRateLimitFailureCount.set(count);
        return count;
    }

    @Override
    public void incrementEventRateLimitFailureCount(long increment) {
        eventRateLimitFailureCount.addAndGet(increment);
    }

    @Override
    public long getByteRateLimitFailureCount() {
        return byteRateLimitFailureCount.get();
    }

    @Override
    public long setByteRateLimitFailureCount(long count) {
        byteRateLimitFailureCount.set(count);
        return count;
    }

    @Override
    public void incrementByteRateLimitFailureCount(long increment) {
        byteRateLimitFailureCount.addAndGet(increment);
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
                .add("ignoredEventCount", ignoredEventCount.get())
                .add("eventRateLimitFailureCount", eventRateLimitFailureCount.get())
                .add("byteRateLimitFailureCount", byteRateLimitFailureCount.get())
                .add("unavailableBufferSpaceFailureCount", unavailableBufferSpaceFailureCount.get())
                .add("redisPushFailureCount", redisPushFailureCount.get())
                .add("redisPushSuccessCount", redisPushSuccessCount.get())
                .toString();
    }

}
