package com.vlkan.log4j2.redis.appender;

import org.junit.rules.ExternalResource;
import redis.embedded.RedisExecProvider;
import redis.embedded.RedisServer;

public class RedisServerResource extends ExternalResource {

    private final int port;

    private final RedisServer redisServer;

    public RedisServerResource(int port, String password) {
        this.port = port;
        try {
            RedisExecProvider redisExecProvider = RedisExecProvider.defaultProvider();
            this.redisServer = RedisServer
                    .builder()
                    .redisExecProvider(redisExecProvider)
                    .port(port)
                    .setting("requirepass " + password)
                    .build();
        } catch (Throwable error) {
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
