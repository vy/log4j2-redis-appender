/*
 * Copyright 2017-2021 Volkan Yazıcı
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
import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import redis.embedded.RedisSentinel;

class RedisSentinelExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private final int port;

    private final int masterPort;

    private final String masterName;

    private final RedisSentinel sentinel;

    RedisSentinelExtension(int port, int masterPort, String masterName) {
        this.port = port;
        this.masterPort = masterPort;
        this.masterName = masterName;
        try {
            this.sentinel = RedisSentinel
                    .builder()
                    .bind("0.0.0.0")
                    .port(port)
                    .masterPort(masterPort)
                    .masterName(masterName)
                    .build();
        } catch (Exception error) {
            String message = String.format(
                    "failed creating Redis sentinel (port=%d, masterPort=%d, masterName=%s)",
                    port, masterPort, masterName);
            throw new RuntimeException(message, error);
        }
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        LOGGER.debug(
                "starting Redis sentinel (port={}, masterPort={}, masterName={})",
                port, masterPort, masterName);
        sentinel.start();
    }

    @Override
    public void afterEach(ExtensionContext ignored) {
        LOGGER.debug(
                "stopping Redis sentinel (port={}, masterPort={}, masterName={})",
                port, masterPort, masterName);
        sentinel.stop();
    }

    @Override
    public String toString() {
        return "RedisSentinelExtension{" +
                "port=" + port +
                ", masterPort=" + masterPort +
                ", masterName='" + masterName + '\'' +
                '}';
    }

}
