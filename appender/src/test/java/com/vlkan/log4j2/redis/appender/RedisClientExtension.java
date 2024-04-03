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
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import redis.clients.jedis.Jedis;

class RedisClientExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private final String host;

    private final int port;

    private final String username ;
    private final String password;

    private final Jedis client;

    RedisClientExtension(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.client = new Jedis(host, port);
    }

    @Override
    public void beforeEach(ExtensionContext ignored) {
        LOGGER.debug("starting Redis client (host={}, port={})", host, port);
        client.connect();
        client.auth(username, password);
    }

    @Override
    public void afterEach(ExtensionContext ignored) {
        LOGGER.debug("stopping Redis client (host={}, port={})", host, port);
        client.disconnect();
    }

    Jedis getClient() {
        return client;
    }

    @Override
    public String toString() {
        return String.format("RedisClientResource{host=%s, port=%d}", host, port);
    }

}
