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
    public void append_should_work_when_server_becomes_reachable_again() throws InterruptedException {

        // Create the logger.
        LoggerContext loggerContext = loggerContextExtension.getLoggerContext();
        Logger logger = loggerContext.getLogger(RedisAppenderReconnectTest.class.getCanonicalName());

        // Try to append the 1st message.
        String message1 = "append should succeed";
        append(logger, message1);
        Thread.sleep(2_000);

        // Verify the persistence of the 1st message.
        Jedis redisClient = redisClientExtension.getClient();
        String message1Json = redisClient.rpop(RedisAppenderTestConfig.REDIS_KEY);
        Assertions.assertThat(message1Json).contains(message1);

        // Stop the server.
        LOGGER.debug("stopping server");
        RedisServer redisServer = redisServerExtension.getRedisServer();
        redisServer.stop();



        try {
            try {
                append(logger, "append should fail silently");
                Thread.sleep(2_000);
                append(logger, "append should fail loudly");
                throw new IllegalStateException("should not have reached here");
            } catch (Throwable error) {
                Assertions.assertThat(error).isInstanceOf(AppenderLoggingException.class);
                Assertions.assertThat(error.getCause()).isNotNull();
                Assertions.assertThat(error.getCause()).hasMessageContaining("Unexpected end of stream.");
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
        LOGGER.debug("trying to append: {}", message);
        logger.info(message);
    }

}
