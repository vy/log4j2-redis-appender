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

public class RedisAppenderSentinelTest {

    private static final String CLASS_NAME = RedisAppenderSentinelTest.class.getSimpleName();

    private static final Logger LOGGER = StatusLogger.getLogger();

    private static final int MIN_MESSAGE_COUNT = 1;

    private static final int MAX_MESSAGE_COUNT = 10;

    private final String redisHost = NetworkUtils.localHostName();

    private final int redisPort = NetworkUtils.findUnusedPort(redisHost);

    private final int redisSentinelPort = NetworkUtils.findUnusedPort(redisHost);

    private final String redisSentinelMasterName = String.format("%s-RedisSentinelMasterName-%s:%d", CLASS_NAME, redisHost, redisPort);

    private final String redisPassword = String.format("%s-RedisPassword-%s:%d", CLASS_NAME, redisHost, redisPort);

    private final String redisKey = String.format("%s-RedisKey-%s:%d", CLASS_NAME, redisHost, redisPort);

    private final String redisAppenderName = String.format("%s-RedisAppender-%s-%d", CLASS_NAME, redisHost, redisPort);

    @Order(1)
    @RegisterExtension
    final RedisServerExtension redisServerExtension = new RedisServerExtension(redisPort, redisPassword);

    @Order(2)
    @RegisterExtension
    final RedisSentinelExtension redisSentinelExtension =
            new RedisSentinelExtension(redisSentinelPort, redisPort, redisSentinelMasterName);

    @Order(3)
    @RegisterExtension
    final RedisClientExtension redisClientExtension = new RedisClientExtension(redisHost, redisPort, redisPassword);

    @Order(4)
    @RegisterExtension
    final LoggerContextExtension loggerContextExtension =
            new LoggerContextExtension(
                    CLASS_NAME,
                    redisAppenderName,
                    configBuilder -> configBuilder.add(configBuilder
                            .newAppender(redisAppenderName, "RedisAppender")
                            .addAttribute("sentinelNodes", redisHost + ":" + redisSentinelPort)
                            .addAttribute("sentinelMaster", redisSentinelMasterName)
                            .addAttribute("password", redisPassword)
                            .addAttribute("key", redisKey)
                            .addAttribute("ignoreExceptions", false)
                            .add(configBuilder
                                    .newLayout("PatternLayout")
                                    .addAttribute("pattern", "%level %msg"))));

    @Test
    void appended_messages_should_be_persisted() {

        // Create the logger.
        LOGGER.debug("creating the logger");
        Logger logger = loggerContextExtension
                .getLoggerContext()
                .getLogger(RedisAppenderSentinelTest.class.getCanonicalName());

        // Create and log the messages.
        int expectedMessageCount = MIN_MESSAGE_COUNT + RANDOM.nextInt(MAX_MESSAGE_COUNT - MIN_MESSAGE_COUNT);
        LOGGER.debug("logging {} messages", expectedMessageCount);
        RedisTestMessage[] expectedLogMessages = RedisTestMessage.createRandomArray(expectedMessageCount);
        for (RedisTestMessage expectedLogMessage : expectedLogMessages) {
            logger.log(expectedLogMessage.level, expectedLogMessage.message);
        }

        // Verify the amount of persisted messages.
        Jedis jedis = redisClientExtension.getClient();
        LOGGER.debug("waiting for the logged messages to be persisted");
        Awaitility
                .await()
                .pollDelay(Duration.ofMillis(100))
                .atMost(Duration.ofSeconds(10))
                .until(() -> {
                    long persistedMessageCount = jedis.llen(redisKey);
                    return persistedMessageCount == expectedLogMessages.length;
                });

        // Verify the content of persisted messages.
        LOGGER.debug("checking logged messages");
        Jedis redisClient = redisClientExtension.getClient();
        for (int messageIndex = 0; messageIndex < expectedMessageCount; messageIndex++) {
            RedisTestMessage expectedLogMessage = expectedLogMessages[messageIndex];
            String expectedSerializedMessage = String.format(
                    "%s %s",
                    expectedLogMessage.level,
                    expectedLogMessage.message);
            String actualSerializedMessage = redisClient.lpop(redisKey);
            try {
                Assertions
                        .assertThat(actualSerializedMessage)
                        .isEqualTo(expectedSerializedMessage);
            } catch (AssertionError error) {
                String message = String.format(
                        "comparison failure (messageIndex=%d, messageCount=%d)",
                        messageIndex,
                        expectedMessageCount);
                throw new RuntimeException(message, error);
            }
        }

        // Verify the throttler counters.
        Appender appender = loggerContextExtension
                .getConfig()
                .getAppender(redisAppenderName);
        Assertions.assertThat(appender).isInstanceOf(RedisAppender.class);
        RedisThrottlerJmxBean jmxBean = ((RedisAppender) appender).getJmxBean();
        Assertions.assertThat(jmxBean.getTotalEventCount()).isEqualTo(expectedMessageCount);
        Assertions.assertThat(jmxBean.getIgnoredEventCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getEventRateLimitFailureCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getByteRateLimitFailureCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getUnavailableBufferSpaceFailureCount()).isEqualTo(0);
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(expectedMessageCount);
        Assertions.assertThat(jmxBean.getRedisPushFailureCount()).isEqualTo(0);

    }

}
