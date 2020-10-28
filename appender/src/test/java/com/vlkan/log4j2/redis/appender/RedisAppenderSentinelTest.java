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

    private static final Logger LOGGER = StatusLogger.getLogger();

    private static final int MIN_MESSAGE_COUNT = 1;

    private static final int MAX_MESSAGE_COUNT = 10;

    @Order(1)
    @RegisterExtension
    final RedisServerExtension redisServerExtension =
            new RedisServerExtension(
                    RedisAppenderSentinelTestConfig.REDIS_PORT,
                    RedisAppenderSentinelTestConfig.REDIS_PASSWORD);

    @Order(2)
    @RegisterExtension
    final RedisSentinelExtension redisSentinelExtension =
            new RedisSentinelExtension(
                    RedisAppenderSentinelTestConfig.REDIS_SENTINEL_PORT,
                    RedisAppenderSentinelTestConfig.REDIS_PORT,
                    RedisAppenderSentinelTestConfig.REDIS_SENTINEL_MASTER_NAME);

    @Order(3)
    @RegisterExtension
    final RedisClientExtension redisClientExtension =
            new RedisClientExtension(
                    RedisAppenderSentinelTestConfig.REDIS_HOST,
                    RedisAppenderSentinelTestConfig.REDIS_PORT,
                    RedisAppenderSentinelTestConfig.REDIS_PASSWORD);

    @Order(4)
    @RegisterExtension
    final LoggerContextExtension loggerContextExtension =
            new LoggerContextExtension(
                    RedisAppenderSentinelTestConfig.LOG4J2_CONFIG_FILE_URI);

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
                    Long persistedMessageCount = jedis.llen(RedisAppenderTestConfig.REDIS_KEY);
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
            String actualSerializedMessage =
                    redisClient.lpop(RedisAppenderSentinelTestConfig.REDIS_KEY);
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
                .getLoggerContext()
                .getConfiguration()
                .getAppender(RedisAppenderSentinelTestConfig.LOG4J2_APPENDER_NAME);
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
