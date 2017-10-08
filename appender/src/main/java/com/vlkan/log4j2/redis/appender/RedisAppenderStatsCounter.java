package com.vlkan.log4j2.redis.appender;

import com.google.common.base.MoreObjects;
import jsr166e.LongAdder;

class RedisAppenderStatsCounter implements RedisAppenderStats {

    private final LongAdder totalEventCount = new LongAdder();

    private final LongAdder ignoredEventCount = new LongAdder();

    private final LongAdder eventRateLimitFailureCount = new LongAdder();

    private final LongAdder byteRateLimitFailureCount = new LongAdder();

    private final LongAdder unavailableBufferSpaceFailureCount = new LongAdder();

    private final LongAdder redisPushFailureCount = new LongAdder();

    private final LongAdder redisPushSuccessCount = new LongAdder();

    public RedisAppenderStatsCounter() {
        // Do nothing.
    }

    @Override
    public long getTotalEventCount() {
        return totalEventCount.sum();
    }

    @Override
    public long getIgnoredEventCount() {
        return ignoredEventCount.sum();
    }

    @Override
    public long getEventRateLimitFailureCount() {
        return eventRateLimitFailureCount.sum();
    }

    @Override
    public long getByteRateLimitFailureCount() {
        return byteRateLimitFailureCount.sum();
    }

    @Override
    public long getUnavailableBufferSpaceFailureCount() {
        return unavailableBufferSpaceFailureCount.sum();
    }

    @Override
    public long getRedisPushFailureCount() {
        return redisPushFailureCount.sum();
    }

    @Override
    public long getRedisPushSuccessCount() {
        return redisPushSuccessCount.sum();
    }

    void recordNewEvent(long eventCount) {
        totalEventCount.add(eventCount);
    }

    void recordIgnoredEvent(long eventCount) {
        ignoredEventCount.add(eventCount);
    }

    void recordEventRateLimitFailure(long eventCount) {
        eventRateLimitFailureCount.add(eventCount);
    }

    void recordByteRateLimitFailure(long eventCount) {
        byteRateLimitFailureCount.add(eventCount);
    }

    void recordUnavailableBufferSpaceFailure(long eventCount) {
        unavailableBufferSpaceFailureCount.add(eventCount);
    }

    void recordPushSuccess(long eventCount) {
        redisPushSuccessCount.add(eventCount);
    }

    void recordPushFailure(long eventCount) {
        redisPushFailureCount.add(eventCount);
    }

    @Override
    public String toString() {
        return MoreObjects
                .toStringHelper(this)
                .add("totalEventCount", getTotalEventCount())
                .add("ignoredEventCount", getIgnoredEventCount())
                .add("eventRateLimitFailureCount", getEventRateLimitFailureCount())
                .add("byteRateLimitFailureCount", getByteRateLimitFailureCount())
                .add("unavailableBufferSpaceFailureCount", getUnavailableBufferSpaceFailureCount())
                .add("redisPushFailureCount", getRedisPushFailureCount())
                .add("redisPushSuccessCount", getRedisPushSuccessCount())
                .toString();
    }
}
