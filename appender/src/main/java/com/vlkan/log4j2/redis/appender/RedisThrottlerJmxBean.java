package com.vlkan.log4j2.redis.appender;

public interface RedisThrottlerJmxBean {

    /**
     * Number of events received by the appender.
     */
    long getTotalEventCount();

    long setTotalEventCount(long count);

    void incrementTotalEventCount(long count);

    /**
     * Number of events dropped due to a previous internal (e.g., Redis push) failure.
     */
    long getIgnoredEventCount();

    long setIgnoredEventCount(long count);

    void incrementIgnoredEventCount(long increment);

    /**
     * Number of events dropped due to event rate limit violation.
     */
    long getEventRateLimitFailureCount();

    long setEventRateLimitFailureCount(long count);

    void incrementEventRateLimitFailureCount(long increment);

    /**
     * Number of events dropped due to byte rate limit violation.
     */
    long getByteRateLimitFailureCount();

    long setByteRateLimitFailureCount(long count);

    void incrementByteRateLimitFailureCount(long increment);

    /**
     * Number of events dropped due to unavailable buffer space while queueing.
     */
    long getUnavailableBufferSpaceFailureCount();

    long setUnavailableBufferSpaceFailureCount(long count);

    void incrementUnavailableBufferSpaceFailureCount(long increment);

    /**
     * Number of failed Redis pushes.
     */
    long getRedisPushFailureCount();

    long setRedisPushFailureCount(int count);

    void incrementRedisPushFailureCount(int increment);

    /**
     * Number of succeeded Redis pushes.
     */
    long getRedisPushSuccessCount();

    long setRedisPushSuccessCount(int count);

    void incrementRedisPushSuccessCount(int increment);

}
