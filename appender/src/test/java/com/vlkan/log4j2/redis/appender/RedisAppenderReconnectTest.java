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
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.status.StatusLogger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import redis.clients.jedis.Jedis;
import redis.embedded.RedisServer;

public class RedisAppenderReconnectTest {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private static final String LOGGER_PREFIX = "[" + RedisAppenderReconnectTest.class.getSimpleName() + "]";

    @Order(1)
    @RegisterExtension
    final RedisServerExtension redisServerExtension =
            new RedisServerExtension(
                    RedisAppenderReconnectTestConfig.REDIS_PORT,
                    RedisAppenderReconnectTestConfig.REDIS_PASSWORD);

    @Order(2)
    @RegisterExtension
    final RedisClientExtension redisClientExtension =
            new RedisClientExtension(
                    RedisAppenderReconnectTestConfig.REDIS_HOST,
                    RedisAppenderReconnectTestConfig.REDIS_PORT,
                    RedisAppenderReconnectTestConfig.REDIS_PASSWORD);

    @Order(3)
    @RegisterExtension
    final LoggerContextExtension loggerContextExtension =
            new LoggerContextExtension(
                    RedisAppenderReconnectTestConfig.LOG4J2_CONFIG_FILE_URI);

    @Test
    public void append_should_work_when_server_becomes_reachable_again() throws InterruptedException {

        // Verify the buffer size.
        Assertions
                .assertThat(RedisAppenderReconnectTestConfig.BATCH_SIZE)
                .as("This test needs a `batchSize` of 1, so that each append operation will trigger a flush.")
                .isEqualTo(1);

        // Verify the flush period.
        Assertions
                .assertThat(RedisAppenderReconnectTestConfig.FLUSH_PERIOD_MILLIS)
                .as("This test needs a `flushPeriodMillis` long enough that it won't kick in during the lifetime of the test.")
                .isGreaterThanOrEqualTo(60_000);

        // Create the logger.
        LOGGER.debug("{} creating the logger", LOGGER_PREFIX);
        LoggerContext loggerContext = loggerContextExtension.getLoggerContext();
        Logger logger = loggerContext.getLogger(RedisAppenderReconnectTest.class.getCanonicalName());

        // Try to append the 1st message.
        LOGGER.debug("{} logging the 1st message", LOGGER_PREFIX);
        logger.error("1st");

        // Give some slack to the throttler to get notified by the buffer update.
        Thread.sleep(500);

        // Verify the persistence of the 1st message.
        Jedis jedis = redisClientExtension.getClient();
        String persistedMessage1 = jedis.lpop(RedisAppenderReconnectTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessage1).isEqualTo("1st");

        // Verify the throttler counter after the 1st message.
        RedisAppender appender = loggerContextExtension
                .getLoggerContext()
                .getConfiguration()
                .getAppender(RedisAppenderReconnectTestConfig.LOG4J2_APPENDER_NAME);
        RedisThrottlerJmxBean jmxBean = appender.getJmxBean();
        Assertions.assertThat(jmxBean.getTotalEventCount()).isEqualTo(1);
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(1);
        Assertions.assertThat(jmxBean.getRedisPushFailureCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getIgnoredEventCount()).isEqualTo(0);

        // Stop the server.
        LOGGER.debug("{} stopping the server", LOGGER_PREFIX);
        RedisServer redisServer = redisServerExtension.getRedisServer();
        jedis.close();
        redisServer.stop();

        // Try to append the 2nd message.
        LOGGER.debug("{} logging the 2nd message, which should fail silently", LOGGER_PREFIX);
        logger.error("2nd");

        // Give some slack to the throttler to get notified by the buffer update.
        Thread.sleep(500);

        // Verify the throttler counter after the 2nd message.
        Assertions.assertThat(jmxBean.getTotalEventCount()).isEqualTo(2);
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(1);
        Assertions.assertThat(jmxBean.getRedisPushFailureCount()).isEqualTo(1);
        Assertions.assertThat(jmxBean.getIgnoredEventCount()).isEqualTo(0);

        // Try to append the 3rd message.
        LOGGER.debug("{} logging the 3rd message, which should fail loudly", LOGGER_PREFIX);
        Assertions
                .assertThatThrownBy(() -> logger.error("3rd"))
                .isInstanceOf(AppenderLoggingException.class)
                .cause()
                .cause()
                .hasMessage("Unexpected end of stream.");

        // Verify the throttler counter after the 3rd message.
        Assertions.assertThat(jmxBean.getTotalEventCount()).isEqualTo(3);
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(1);
        Assertions.assertThat(jmxBean.getRedisPushFailureCount()).isEqualTo(1);
        Assertions.assertThat(jmxBean.getIgnoredEventCount()).isEqualTo(1);

        // Start the server again.
        LOGGER.debug("{} starting server again", LOGGER_PREFIX);
        redisServer.start();
        jedis.connect();
        jedis.auth(RedisAppenderReconnectTestConfig.REDIS_PASSWORD);

        // Try to append the 4th message.
        LOGGER.debug("{} logging the 4th message", LOGGER_PREFIX);
        logger.error("4th");

        // Give some slack to the throttler to get notified by the buffer update.
        Thread.sleep(500);

        // Verify that the 2nd message is *not* persisted.
        String persistedMessage4 = jedis.lpop(RedisAppenderReconnectTestConfig.REDIS_KEY);
        Assertions.assertThat(persistedMessage4).isEqualTo("4th");

        // Verify the throttler counter after the 2nd message.
        Assertions.assertThat(jmxBean.getTotalEventCount()).isEqualTo(4);
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(2);
        Assertions.assertThat(jmxBean.getRedisPushFailureCount()).isEqualTo(1);
        Assertions.assertThat(jmxBean.getIgnoredEventCount()).isEqualTo(1);

    }

}
