package com.vlkan.log4j2.redis.appender;

import org.junit.rules.ExternalResource;
import redis.embedded.RedisServer;

class RedisServerResource extends ExternalResource {

    private final int port;

    private final RedisServer redisServer;

    RedisServerResource(int port, String password) {
        this.port = port;
        try {
            this.redisServer = RedisServer
                    .builder()
                    .port(port)
                    .setting("requirepass " + password)
                    .build();
        } catch (Throwable error) {
            String message = String.format("failed creating Redis server (port=%d)", port);
            throw new RuntimeException(message, error);
        }
    }

    RedisServer getRedisServer() {
        return redisServer;
    }

    @Override
    protected void before() {
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
