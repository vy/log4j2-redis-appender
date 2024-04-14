/*
 * Copyright 2017-2023 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
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

    private static final String CLASS_NAME = RedisAppenderShutdownTest.class.getSimpleName();

    private static final String LOGGER_PREFIX = "[" + CLASS_NAME + "]";

    private final String redisHost = NetworkUtils.localHostName();

    private final int redisPort = NetworkUtils.findUnusedPort(redisHost);

    private final String redisUsername = String.format("%s-RedisUsername-%s:%d", CLASS_NAME, redisHost, redisPort);

    private final String redisPassword = String.format("%s-RedisPassword-%s:%d", CLASS_NAME, redisHost, redisPort);

    private final String redisKey = String.format("%s-RedisKey-%s:%d", CLASS_NAME, redisHost, redisPort);

    private final String redisAppenderName = String.format("%s-RedisAppender-%s-%d", CLASS_NAME, redisHost, redisPort);

    @Order(1)
    @RegisterExtension
    final RedisServerExtension redisServerExtension = new RedisServerExtension(redisPort, redisUsername, redisPassword);

    @Order(2)
    @RegisterExtension
    final RedisClientExtension redisClientExtension = new RedisClientExtension(redisHost, redisPort, redisUsername, redisPassword);

    @Order(3)
    @RegisterExtension
    final LoggerContextExtension loggerContextExtension =
            new LoggerContextExtension(
                    CLASS_NAME,
                    redisAppenderName,
                    configBuilder -> configBuilder.add(configBuilder
                            .newAppender(redisAppenderName, "RedisAppender")
                            .addAttribute("host", redisHost)
                            .addAttribute("port", redisPort)
                            .addAttribute("username", redisUsername)
                            .addAttribute("password", redisPassword)
                            .addAttribute("key", redisKey)
                            .addAttribute("ignoreExceptions", false)
                            .add(configBuilder
                                    .newLayout("PatternLayout")
                                    .addAttribute("pattern", "%m"))
                            .addComponent(configBuilder
                                    .newComponent("RedisThrottlerConfig")
                                    // This test needs a `flushPeriodMillis` long enough that it won't kick in during the lifetime of the test.
                                    .addAttribute("flushPeriodMillis", 60_000L)
                                    // This test needs a `batchSize` of 2, so that it is easy to play around with the batch and fill it up to trigger a flush.
                                    .addAttribute("batchSize", 2))));

    @Test
    void shutdown_should_flush_the_buffer() throws InterruptedException {

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
        long persistedMessageCount1 = jedis.llen(redisKey);
        Assertions.assertThat(persistedMessageCount1).isEqualTo(0);

        // Verify the throttler counter after the 1st message.
        RedisAppender appender = loggerContextExtension
                .getConfig()
                .getAppender(redisAppenderName);
        RedisThrottlerJmxBean jmxBean = appender.getJmxBean();
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(0);

        // Log the 2nd message.
        LOGGER.debug("{} logging the 2nd message", LOGGER_PREFIX);
        logger.error("2nd");

        // Give some slack to the throttler to get notified by the buffer update.
        Thread.sleep(500);

        // Verify that the buffer (containing the 1st and 2nd messages) is flushed due to overflow.
        long persistedMessageCount2 = jedis.llen(redisKey);
        Assertions.assertThat(persistedMessageCount2).isEqualTo(2);
        String persistedMessage1 = jedis.lpop(redisKey);
        Assertions.assertThat(persistedMessage1).isEqualTo("1st");
        String persistedMessage2 = jedis.lpop(redisKey);
        Assertions.assertThat(persistedMessage2).isEqualTo("2nd");

        // Verify the throttler counter after the 2nd message.
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(2);

        // Log the 3rd message.
        LOGGER.debug("{} logging the 3rd message", LOGGER_PREFIX);
        logger.error("3rd");

        // Give some slack to the throttler to get notified by the buffer update.
        Thread.sleep(500);

        // Verify that the 3rd message is *not* persisted.
        long persistedMessageCount3 = jedis.llen(redisKey);
        Assertions.assertThat(persistedMessageCount3).isEqualTo(0);

        // Verify the throttler counter after the 3rd message.
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(2);

        // Stop the appender.
        appender.stop();

        // Verify that the buffer (containing the 1st message) is flushed due to shut down.
        long persistedMessageCount4 = jedis.llen(redisKey);
        Assertions.assertThat(persistedMessageCount4).isEqualTo(1);
        String persistedMessage3 = jedis.lpop(redisKey);
        Assertions.assertThat(persistedMessage3).isEqualTo("3rd");

        // Verify the throttler counter after the shutdown.
        Assertions.assertThat(jmxBean.getRedisPushSuccessCount()).isEqualTo(3);

    }

}
