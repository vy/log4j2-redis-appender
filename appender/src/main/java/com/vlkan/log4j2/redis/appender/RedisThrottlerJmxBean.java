/*
 * Copyright 2017-2022 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */
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
