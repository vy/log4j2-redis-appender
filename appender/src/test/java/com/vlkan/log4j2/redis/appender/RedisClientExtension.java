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

    private final String password;

    private final Jedis client;

    RedisClientExtension(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.client = new Jedis(host, port);
    }

    @Override
    public void beforeEach(ExtensionContext ignored) {
        LOGGER.debug("starting Redis client (host={}, port={})", host, port);
        client.connect();
        client.auth(password);
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
