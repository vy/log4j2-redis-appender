package com.vlkan.log4j2.redis.appender;

import org.junit.rules.ExternalResource;
import redis.embedded.RedisSentinel;

class RedisSentinelResource extends ExternalResource {

    private final int port;

    private final int masterPort;

    private final String masterName;

    private final RedisSentinel sentinel;

    RedisSentinelResource(int port, int masterPort, String masterName) {
        this.port = port;
        this.masterPort = masterPort;
        this.masterName = masterName;
        try {
            this.sentinel = RedisSentinel
                    .builder()
                    .port(port)
                    .masterPort(masterPort)
                    .masterName(masterName)
                    .build();
        } catch (Throwable error) {
            String message = String.format("failed creating Redis sentinel (port=%d)", port);
            throw new RuntimeException(message, error);
        }
    }

    @Override
    protected void before() {
        sentinel.start();
    }

    @Override
    protected void after() {
        sentinel.stop();
    }

    @Override
    public String toString() {
        return "RedisSentinelResource{" +
                "port=" + port +
                ", masterPort=" + masterPort +
                ", masterName='" + masterName + '\'' +
                '}';
    }

}
