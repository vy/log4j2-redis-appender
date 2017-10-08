package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.junit.ClassRule;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.rules.RuleChain;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisAppenderTest {

    private static final DebugLogger LOGGER = new DebugLogger(RedisAppenderTest.class, true);

    private static final Random RANDOM = new Random(0);

    private static final int MIN_MESSAGE_COUNT = 1;

    private static final int MAX_MESSAGE_COUNT = 100;

    public static final String REDIS_KEY = "log4j2-messages";

    public static final String REDIS_HOST = "localhost";

    public static final String REDIS_PASSWORD = "toosecret";

    public static final int REDIS_PORT = 63790;

    private static final RedisServerResource REDIS_SERVER_RESOURCE = new RedisServerResource(REDIS_PORT, REDIS_PASSWORD);

    private static final RedisClientResource REDIS_CLIENT_RESOURCE = new RedisClientResource(REDIS_HOST, REDIS_PORT, REDIS_PASSWORD);

    private static final String CONFIG_FILE_NAME = "log4j2.RedisAppenderTest.xml";

    public static final URI CONFIG_FILE_URI = createConfigFileUri(CONFIG_FILE_NAME);

    private static final LoggerContextResource LOGGER_CONTEXT_RESOURCE = new LoggerContextResource(CONFIG_FILE_URI);

    @ClassRule
    public static final RuleChain RULE_CHAIN = RuleChain
            .outerRule(REDIS_SERVER_RESOURCE)
            .around(REDIS_CLIENT_RESOURCE)
            .around(LOGGER_CONTEXT_RESOURCE);

    private static class LogMessage {

        private static final Level[] LEVELS = Level.values();

        private static final int MIN_MESSAGE_LENGTH = 10;

        private static final int MAX_MESSAGE_LENGTH = 100;

        private final Level level;

        private final String message;

        private static volatile int counter = 0;

        private LogMessage(Level level, String message) {
            this.level = level;
            this.message = message;
        }

        private static LogMessage createRandom() {
            int levelIndex = RANDOM.nextInt(LEVELS.length);
            Level level = LEVELS[levelIndex];
            int messageLength = MIN_MESSAGE_LENGTH + RANDOM.nextInt(MAX_MESSAGE_LENGTH - MIN_MESSAGE_LENGTH);
            String prefix = String.format("[%d] ", counter++);
            StringBuilder messageBuilder = new StringBuilder(prefix);
            while (messageBuilder.length() < messageLength) {
                char messageChar = (char) RANDOM.nextInt(Character.MAX_VALUE);
                if (Character.isLetterOrDigit(messageChar)) {
                    messageBuilder.append(messageChar);
                }
            }
            String message = messageBuilder.toString();
            return new LogMessage(level, message);
        }

        private static LogMessage[] createRandomArray(int count) {
            LogMessage[] messages = new LogMessage[count];
            for (int i = 0; i < count; i++) {
                messages[i] = createRandom();
            }
            return messages;
        }

    }

    private static URI createConfigFileUri(String configFileName) {
        try {
            return new URI("classpath:" + configFileName);
        } catch (URISyntaxException error) {
            String message = String.format("failed finding Log4j config (filename=%s)", configFileName);
            throw new RuntimeException(message, error);
        }
    }

    @Test
    public void test_messages_are_enqueued_to_redis() throws IOException, InterruptedException {

        LOGGER.debug("creating the logger");
        Logger logger = LOGGER_CONTEXT_RESOURCE.getLoggerContext().getLogger(RedisAppenderTest.class.getCanonicalName());

        int expectedMessageCount = MIN_MESSAGE_COUNT + RANDOM.nextInt(MAX_MESSAGE_COUNT - MIN_MESSAGE_COUNT);
        LOGGER.debug("logging %d messages", expectedMessageCount);
        LogMessage[] expectedLogMessages = LogMessage.createRandomArray(expectedMessageCount);
        for (LogMessage expectedLogMessage : expectedLogMessages) {
            logger.log(expectedLogMessage.level, expectedLogMessage.message);
        }

        LOGGER.debug("waiting for throttler to kick in");
        Thread.sleep(1_000);

        LOGGER.debug("checking logged messages");
        Jedis redisClient = REDIS_CLIENT_RESOURCE.getClient();
        for (int messageIndex = 0; messageIndex < expectedMessageCount; messageIndex++) {
            LogMessage expectedLogMessage = expectedLogMessages[messageIndex];
            String expectedSerializedMessage = String.format("%s %s", expectedLogMessage.level, expectedLogMessage.message);
            String actualSerializedMessage = redisClient.lpop(REDIS_KEY);
            try {
                assertThat(actualSerializedMessage).isEqualTo(expectedSerializedMessage);
            } catch (ComparisonFailure comparisonFailure) {
                String message = String.format("comparison failure (messageIndex=%d, messageCount=%d)", messageIndex, expectedMessageCount);
                throw new RuntimeException(message, comparisonFailure);
            }
        }

        final Appender appender = LOGGER_CONTEXT_RESOURCE.getLoggerContext().getConfiguration().getAppender("REDIS");
        assertThat(appender).isInstanceOf(RedisAppender.class);

        final RedisAppenderStats stats = ((RedisAppender) appender).getStats();
        assertThat(stats.getTotalEventCount()).isEqualTo(expectedMessageCount);
        assertThat(stats.getIgnoredEventCount()).isEqualTo(0);
        assertThat(stats.getEventRateLimitFailureCount()).isEqualTo(0);
        assertThat(stats.getByteRateLimitFailureCount()).isEqualTo(0);
        assertThat(stats.getUnavailableBufferSpaceFailureCount()).isEqualTo(0);
        assertThat(stats.getRedisPushSuccessCount()).isEqualTo(expectedMessageCount);
        assertThat(stats.getRedisPushFailureCount()).isEqualTo(0);
    }

}
