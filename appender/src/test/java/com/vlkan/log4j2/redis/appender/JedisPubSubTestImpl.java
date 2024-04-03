package com.vlkan.log4j2.redis.appender;

import redis.clients.jedis.JedisPubSub;

import java.util.LinkedList;
import java.util.Queue;

public class JedisPubSubTestImpl extends JedisPubSub {

    private Integer messageCount = 0;
    private final Queue<String> messages = new LinkedList<>();
    @Override
    public void onMessage(final String channel, final String message) {
        messageCount++;
        messages.add(message);
        super.onMessage(channel, message);
    }

    @Override
    public void onPong(final String pattern) {
        super.onPong(pattern);
    }

    @Override
    public void onSubscribe(final String channel, final int subscribedChannels) {
        System.out.println("Subscribed to channel: " + channel);
        super.onSubscribe(channel, subscribedChannels);
    }

    public synchronized Integer messageCount() {
        return messageCount;
    }

    public synchronized String nextMessage() {
        return messages.poll();
    }
}
