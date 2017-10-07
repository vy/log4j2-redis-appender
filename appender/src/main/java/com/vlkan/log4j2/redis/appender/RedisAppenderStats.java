package com.vlkan.log4j2.redis.appender;

public interface RedisAppenderStats {

    long getTotalEventCount();

    long getIgnoredEventCount();

    long getRateLimitFailureCount();

    long getUnavailableBufferSpaceFailureCount();

    long getRedisPushFailureCount();

    long getRedisPushSuccessCount();
}
