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
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.status.StatusLogger;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import redis.clients.jedis.Jedis;

import java.time.Duration;

import static com.vlkan.log4j2.redis.appender.RedisTestConstants.RANDOM;

class RedisAppenderTest {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private static final int MIN_MESSAGE_COUNT = 1;

    private static final int MAX_MESSAGE_COUNT = 100;

    @Order(1)
    @RegisterExtension
    final RedisServerExtension redisServerExtension =
            new RedisServerExtension(
                    RedisAppenderTestConfig.REDIS_PORT,
                    RedisAppenderTestConfig.REDIS_PASSWORD);

    @Order(2)
    @RegisterExtension
    final RedisClientExtension redisClientExtension =
            new RedisClientExtension(
                    RedisAppenderTestConfig.REDIS_HOST,
                    RedisAppenderTestConfig.REDIS_PORT,
                    RedisAppenderTestConfig.REDIS_PASSWORD);

    @Order(3)
    @RegisterExtension
    final LoggerContextExtension loggerContextExtension =
            new LoggerContextExtension(
                    RedisAppenderTestConfig.LOG4J2_CONFIG_FILE_URI);

    @Test
    void appended_messages_should_be_persisted() {

        // Create the logger.
        LOGGER.debug("creating the logger");
        Logger logger = loggerContextExtension
                .getLoggerContext()
                .getLogger(RedisAppenderTest.class.getCanonicalName());

        // Create and log the messages.
        int expectedMessageCount = MIN_MESSAGE_COUNT + RANDOM.nextInt(MAX_MESSAGE_COUNT - MIN_MESSAGE_COUNT);
        LOGGER.debug("logging {} messages", expectedMessageCount);
        RedisTestMessage[] expectedLogMessages = RedisTestMessage.createRandomArray(expectedMessageCount);
        for (RedisTestMessage expectedLogMessage : expectedLogMessages) {
            logger.log(expectedLogMessage.level, expectedLogMessage.message);
        }

        // Verify the logging.
        verifyLogging(expectedLogMessages, expectedMessageCount, expectedMessageCount);

    }

    @Test
    void throttler_should_not_flush_same_content_twice() {

        // Create the logger.
        LOGGER.debug("creating the logger");
        Logger logger = loggerContextExtension
                .getLoggerContext()
                .getLogger(RedisAppenderTest.class.getCanonicalName());

        // Verify that batch size is greater than 1 so that we can observe a partially filled batch push.
        Assertions.assertThat(RedisAppenderTestConfig.THROTTLER_BATCH_SIZE).isGreaterThan(1);

        // Log the 1st message.
        RedisTestMessage[] expectedLogMessages1 = RedisTestMessage.createRandomArray(1);
        logger.log(expectedLogMessages1[0].level, expectedLogMessages1[0].message);

        // Verify the 1st message persistence.
        verifyLogging(expectedLogMessages1, 1, 1);

        // Log the 1st message.
        RedisTestMessage[] expectedLogMessages2 = RedisTestMessage.createRandomArray(1);
        logger.log(expectedLogMessages2[0].level, expectedLogMessages2[0].message);

        // Verify the 1st message persistence.
        verifyLogging(expectedLogMessages2, 2, 2);

    }

    private void verifyLogging(
            RedisTestMessage[] expectedLogMessages,
            int expectedTotalEventCount,
            int expectedRedisPushSuccessCount) {

        // Verify the amount of persisted messages.
        Jedis jedis = redisClientExtension.getClient();
        LOGGER.debug("waiting for the logged messages to be persisted");
        int expectedMessageCount = expectedLogMessages.length;
        Awaitility
                .await()
                .pollDelay(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    long persistedMessageCount = jedis.llen(RedisAppenderTestConfig.REDIS_KEY);
                    Assertions.assertThat(persistedMessageCount).isLessThanOrEqualTo(expectedMessageCount);
                    return persistedMessageCount == expectedMessageCount;
                });

        // Verify the content of persisted messages.
        LOGGER.debug("verifying the content of persisted messages");
        Jedis redisClient = redisClientExtension.getClient();
        for (int messageIndex = 0; messageIndex < expectedMessageCount; messageIndex++) {
            RedisTestMessage expectedLogMessage = expectedLogMessages[messageIndex];
            String expectedSerializedMessage = String.format(
                    "%s %s",
                    expectedLogMessage.level,
                    expectedLogMessage.message);
            String actualSerializedMessage = redisClient.lpop(RedisAppenderTestConfig.REDIS_KEY);
            try {
                Assertions.assertThat(actualSerializedMessage).isEqualTo(expectedSerializedMessage);
            } catch (AssertionError error) {
                String message = String.format(
                        "comparison failure (messageIndex=%d, messageCount=%d)",
                        messageIndex,
                        expectedMessageCount);
                throw new AssertionError(message, error);
            }
        }

        // Verify the throttler counters.
        Appender appender = loggerContextExtension
                .getLoggerContext()
                .getConfiguration()
                .getAppender(RedisAppenderTestConfig.LOG4J2_APPENDER_NAME);
        Assertions.assertThat(appender).isInstanceOf(RedisAppender.class);
        RedisThrottlerJmxBean jmxBean = ((RedisAppender) appender).getJmxBean();
        Assertions.assertThat(jmxBean.getTotalEventCount()).isEqualTo(expectedTotalEventCount);
        Assertions.assertThat(jmxBean.getIgnoredEventCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getEventRateLimitFailureCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getByteRateLimitFailureCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getUnavailableBufferSpaceFailureCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(expectedRedisPushSuccessCount);
        Assertions.assertThat(jmxBean.getRedisPushFailureCount()).isEqualTo(0);

    }

}
