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

    RedisServerExtension(int port, String password) {
        this.port = port;
        try {
            this.redisServer = RedisServer
                    .builder()
                    .port(port)
                    .bind("0.0.0.0")
                    .setting("requirepass " + password)
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
