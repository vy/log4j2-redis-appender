package com.vlkan.log4j2.redis.appender;

import redis.clients.jedis.JedisPubSub;

import java.util.LinkedList;
import java.util.Queue;

final class JedisTestSubscriber extends JedisPubSub {

    private Integer messageCount = 0;
    private final Queue<String> messages = new LinkedList<>();

    @Override
    public synchronized void onMessage(final String channel, final String message) {
        messageCount++;
        messages.add(message);
    }

    synchronized Integer messageCount() {
        return messageCount;
    }

    synchronized String nextMessage() {
        return messages.poll();
    }
}
