package com.vlkan.log4j2.redis.appender;

public interface RedisThrottlerJmxBean {

    /**
     * Number of events received by the appender.
     */
    long getTotalEventCount();

    void incrementTotalEventCount(long increment);

    /**
     * Number of events dropped due to a previous internal (e.g., Redis push) failure.
     */
    long getIgnoredEventCount();

    void incrementIgnoredEventCount(long increment);

    /**
     * Number of events dropped due to event rate limit violation.
     */
    long getEventRateLimitFailureCount();

    void incrementEventRateLimitFailureCount(long increment);

    /**
     * Number of events dropped due to byte rate limit violation.
     */
    long getByteRateLimitFailureCount();

    void incrementByteRateLimitFailureCount(long increment);

    /**
     * Number of events dropped due to unavailable buffer space while queueing.
     */
    long getUnavailableBufferSpaceFailureCount();

    void incrementUnavailableBufferSpaceFailureCount(long increment);

    /**
     * Number of failed Redis pushes.
     */
    long getRedisPushFailureCount();

    void incrementRedisPushFailureCount(int increment);

    /**
     * Number of succeeded Redis pushes.
     */
    long getRedisPushSuccessCount();

    void incrementRedisPushSuccessCount(int increment);

}
