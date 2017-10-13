package com.vlkan.log4j2.redis.appender;

import com.google.common.base.MoreObjects;
import com.twitter.jsr166e.LongAdder;

public class RedisThrottlerInternalJmxBean implements RedisThrottlerJmxBean {

    private final LongAdder totalEventCount = new LongAdder();

    private final LongAdder ignoredEventCount = new LongAdder();

    private final LongAdder eventRateLimitFailureCount = new LongAdder();

    private final LongAdder byteRateLimitFailureCount = new LongAdder();

    private final LongAdder unavailableBufferSpaceFailureCount = new LongAdder();

    private final LongAdder redisPushFailureCount = new LongAdder();

    private final LongAdder redisPushSuccessCount = new LongAdder();

    public RedisThrottlerInternalJmxBean() {
        // Do nothing.
    }

    @Override
    public long getTotalEventCount() {
        return totalEventCount.sum();
    }

    @Override
    public void incrementTotalEventCount(long count) {
        totalEventCount.add(count);
    }

    @Override
    public long getIgnoredEventCount() {
        return ignoredEventCount.sum();
    }

    @Override
    public void incrementIgnoredEventCount(long count) {
        ignoredEventCount.add(count);
    }

    @Override
    public long getEventRateLimitFailureCount() {
        return eventRateLimitFailureCount.sum();
    }

    @Override
    public void incrementEventRateLimitFailureCount(long increment) {
        eventRateLimitFailureCount.add(increment);
    }

    @Override
    public long getByteRateLimitFailureCount() {
        return byteRateLimitFailureCount.sum();
    }

    @Override
    public void incrementByteRateLimitFailureCount(long increment) {
        byteRateLimitFailureCount.add(increment);
    }

    @Override
    public long getUnavailableBufferSpaceFailureCount() {
        return unavailableBufferSpaceFailureCount.sum();
    }

    @Override
    public void incrementUnavailableBufferSpaceFailureCount(long increment) {
        unavailableBufferSpaceFailureCount.add(increment);
    }

    @Override
    public long getRedisPushFailureCount() {
        return redisPushFailureCount.sum();
    }

    @Override
    public void incrementRedisPushFailureCount(int increment) {
        redisPushFailureCount.add(increment);
    }

    @Override
    public long getRedisPushSuccessCount() {
        return redisPushSuccessCount.sum();
    }

    @Override
    public void incrementRedisPushSuccessCount(int increment) {
        redisPushSuccessCount.add(increment);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("totalEventCount", totalEventCount.sum())
                .add("ignoredEventCount", ignoredEventCount.sum())
                .add("eventRateLimitFailureCount", eventRateLimitFailureCount.sum())
                .add("byteRateLimitFailureCount", byteRateLimitFailureCount.sum())
                .add("unavailableBufferSpaceFailureCount", unavailableBufferSpaceFailureCount.sum())
                .add("redisPushFailureCount", redisPushFailureCount.sum())
                .add("redisPushSuccessCount", redisPushSuccessCount.sum())
                .toString();
    }

}
