package com.vlkan.log4j2.redis.appender;

import org.junit.rules.ExternalResource;
import redis.clients.jedis.Jedis;

public class RedisClientResource extends ExternalResource {

    private final String host;

    private final int port;

    private final Jedis client;

    public RedisClientResource(String host, int port) {
        this.host = host;
        this.port = port;
        this.client = new Jedis(host, port);
    }

    @Override
    protected void before() throws Throwable {
        client.connect();
    }

    @Override
    protected void after() {
        client.disconnect();
    }

    public Jedis getClient() {
        return client;
    }

    @Override
    public String toString() {
        return String.format("RedisClientResource{host=%s, port=%d}", host, port);
    }

}
