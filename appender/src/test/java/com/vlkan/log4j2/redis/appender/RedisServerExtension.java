/*
 * Copyright 2017-2024 Volkan Yazıcı
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
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import redis.embedded.RedisServer;

class RedisServerExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private final int port;

    private final RedisServer redisServer;

    RedisServerExtension(int port, String username, String password) {
        this.port = port;
        try {
            this.redisServer = RedisServer
                    .builder()
                    .port(port)
                    .bind("0.0.0.0")
                    .setting("user " + username + " on -debug +@all ~* &* >" + password)
                    .build();
        } catch (Exception error) {
            String message = String.format("failed creating Redis server (port=%d)", port);
            throw new RuntimeException(message, error);
        }
    }

    RedisServer getRedisServer() {
        return redisServer;
    }

    @Override
    public void beforeEach(ExtensionContext ignored) {
        LOGGER.debug("starting Redis server (port={})", port);
        redisServer.start();
    }

    @Override
    public void afterEach(ExtensionContext ignored) {
        LOGGER.debug("stopping Redis server (port={})", port);
        redisServer.stop();
    }

    @Override
    public String toString() {
        return String.format("RedisServerExtension{port=%d}", port);
    }

}
