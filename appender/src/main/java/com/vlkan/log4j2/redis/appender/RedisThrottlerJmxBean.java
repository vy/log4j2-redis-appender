package com.vlkan.log4j2.redis.appender;

public interface RedisThrottlerJmxBean {

    long getTotalEventCount();

    long setTotalEventCount(long count);

    void incrementTotalEventCount(long count);

    long getRateLimitFailureCount();

    long setRateLimitFailureCount(long count);

    void incrementRateLimitFailureCount(long increment);

    long getUnavailableBufferSpaceFailureCount();

    long setUnavailableBufferSpaceFailureCount(long count);

    void incrementUnavailableBufferSpaceFailureCount(long increment);

    long getRedisPushFailureCount();

    long setRedisPushFailureCount(int count);

    void incrementRedisPushFailureCount(int increment);

    long getRedisPushSuccessCount();

    long setRedisPushSuccessCount(int count);

    void incrementRedisPushSuccessCount(int increment);

}
