package com.vlkan.log4j2.redis.appender;

import org.junit.rules.ExternalResource;
import redis.embedded.RedisServer;

import java.io.IOException;

public class RedisServerResource extends ExternalResource {

    private final int port;

    private final RedisServer redisServer;

    public RedisServerResource(int port) {
        this.port = port;
        try {
            this.redisServer = new RedisServer(port);
        } catch (IOException error) {
            String message = String.format("failed creating Redis server (port=%d)", port);
            throw new RuntimeException(message, error);
        }
    }

    public int getPort() {
        return port;
    }

    public RedisServer getRedisServer() {
        return redisServer;
    }

    @Override
    protected void before() throws Throwable {
        redisServer.start();
    }

    @Override
    protected void after() {
        redisServer.stop();
    }

    @Override
    public String toString() {
        return String.format("RedisServerResource{port=%d}", port);
    }

}
