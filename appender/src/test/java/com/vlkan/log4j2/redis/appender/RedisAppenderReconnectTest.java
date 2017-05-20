package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.vlkan.log4j2.redis.appender.RedisAppenderTest.CONFIG_FILE_URI;
import static com.vlkan.log4j2.redis.appender.RedisAppenderTest.REDIS_PASSWORD;
import static com.vlkan.log4j2.redis.appender.RedisAppenderTest.REDIS_PORT;
import static org.assertj.core.api.Assertions.assertThat;

public class RedisAppenderReconnectTest {

    private static final DebugLogger LOGGER = new DebugLogger(RedisAppenderReconnectTest.class);

    private static final RedisServerResource REDIS_SERVER_RESOURCE = new RedisServerResource(REDIS_PORT, REDIS_PASSWORD);

    private static final LoggerContextResource LOGGER_CONTEXT_RESOURCE = new LoggerContextResource(CONFIG_FILE_URI);

    @ClassRule
    public static final RuleChain RULE_CHAIN = RuleChain
            .outerRule(REDIS_SERVER_RESOURCE)
            .around(LOGGER_CONTEXT_RESOURCE);

    @Test
    public void test_reconnect() throws URISyntaxException, IOException, InterruptedException {
        LoggerContext loggerContext = LOGGER_CONTEXT_RESOURCE.getLoggerContext();
        RedisServer redisServer = REDIS_SERVER_RESOURCE.getRedisServer();
        try {
            Logger logger = loggerContext.getLogger(RedisAppenderReconnectTest.class.getCanonicalName());
            append(logger, "append should succeed");
            LOGGER.debug("stopping server");
            redisServer.stop();
            try {
                append(logger, "append should fail");
                throw new IllegalStateException("should not have reached here");
            } catch (Throwable error) {
                assertThat(error.getCause()).isInstanceOf(JedisConnectionException.class);
                LOGGER.debug("starting server");
                redisServer.start();
                append(logger, "append should succeed again");
            }
        } finally {
            LOGGER.debug("finally stopping server");
            redisServer.stop();
        }
    }

    private static void append(Logger logger, String message) {
        LOGGER.debug("trying to append: %s", message);
        logger.info(message);
    }

}
