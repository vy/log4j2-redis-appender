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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.status.StatusLogger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import redis.clients.jedis.Jedis;

class RedisAppenderShutdownTest {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private static final String LOGGER_PREFIX = "[" + RedisAppenderShutdownTest.class.getSimpleName() + "]";

    @Order(1)
    @RegisterExtension
    final RedisServerExtension redisServerExtension =
            new RedisServerExtension(
                    RedisAppenderShutdownTestConfig.REDIS_PORT,
                    RedisAppenderShutdownTestConfig.REDIS_PASSWORD);

    @Order(2)
    @RegisterExtension
    final RedisClientExtension redisClientExtension =
            new RedisClientExtension(
                    RedisAppenderShutdownTestConfig.REDIS_HOST,
                    RedisAppenderShutdownTestConfig.REDIS_PORT,
                    RedisAppenderShutdownTestConfig.REDIS_PASSWORD);

    @Order(3)
    @RegisterExtension
    final LoggerContextExtension loggerContextExtension =
            new LoggerContextExtension(
                    RedisAppenderShutdownTestConfig.LOG4J2_CONFIG_FILE_URI);

    @Test
    void shutdown_should_flush_the_buffer() throws InterruptedException {

        // Verify the buffer size.
        Assertions
                .assertThat(RedisAppenderShutdownTestConfig.BATCH_SIZE)
                .as("This test needs a `batchSize` of 2, so that it is easy to play around with the batch and fill it up to trigger a flush.")
                .isEqualTo(2);

        // Verify the flush period.
        Assertions
                .assertThat(RedisAppenderShutdownTestConfig.FLUSH_PERIOD_MILLIS)
                .as("This test needs a `flushPeriodMillis` long enough that it won't kick in during the lifetime of the test.")
                .isGreaterThanOrEqualTo(60_000);

        // Create the logger.
        LOGGER.debug("{} creating the logger", LOGGER_PREFIX);
        Logger logger = loggerContextExtension.getLoggerContext().getLogger(RedisAppenderShutdownTest.class);

        // Log the 1st message.
        LOGGER.debug("{} logging the 1st message", LOGGER_PREFIX);
        logger.error("1st");

        // Give some slack to the throttler to get notified by the buffer update.
        Thread.sleep(500);

        // Verify that the 1st message is *not* persisted.
        Jedis jedis = redisClientExtension.getClient();
        long persistedMessageCount1 = jedis.llen(RedisAppenderShutdownTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessageCount1).isEqualTo(0);

        // Verify the throttler counter after the 1st message.
        RedisAppender appender = loggerContextExtension
                .getLoggerContext()
                .getConfiguration()
                .getAppender(RedisAppenderShutdownTestConfig.LOG4J2_APPENDER_NAME);
        RedisThrottlerJmxBean jmxBean = appender.getJmxBean();
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(0);

        // Log the 2nd message.
        LOGGER.debug("{} logging the 2nd message", LOGGER_PREFIX);
        logger.error("2nd");

        // Give some slack to the throttler to get notified by the buffer update.
        Thread.sleep(500);

        // Verify that the buffer (containing the 1st and 2nd messages) is flushed due to overflow.
        long persistedMessageCount2 = jedis.llen(RedisAppenderShutdownTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessageCount2).isEqualTo(2);
        String persistedMessage1 = jedis.lpop(RedisAppenderShutdownTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessage1).isEqualTo("1st");
        String persistedMessage2 = jedis.lpop(RedisAppenderShutdownTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessage2).isEqualTo("2nd");

        // Verify the throttler counter after the 2nd message.
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(2);

        // Log the 3rd message.
        LOGGER.debug("{} logging the 3rd message", LOGGER_PREFIX);
        logger.error("3rd");

        // Give some slack to the throttler to get notified by the buffer update.
        Thread.sleep(500);

        // Verify that the 3rd message is *not* persisted.
        long persistedMessageCount3 = jedis.llen(RedisAppenderShutdownTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessageCount3).isEqualTo(0);

        // Verify the throttler counter after the 3rd message.
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(2);

        // Stop the appender.
        appender.stop();

        // Verify that the buffer (containing the 1st message) is flushed due to shut down.
        long persistedMessageCount4 = jedis.llen(RedisAppenderShutdownTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessageCount4).isEqualTo(1);
        String persistedMessage3 = jedis.lpop(RedisAppenderShutdownTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessage3).isEqualTo("3rd");

        // Verify the throttler counter after the shutdown.
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(3);

    }

}
