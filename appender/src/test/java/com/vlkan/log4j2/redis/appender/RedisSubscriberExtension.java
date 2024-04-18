/*
 * Copyright 2017-2024 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */
package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.LinkedList;
import java.util.Queue;

final class RedisSubscriberExtension extends JedisPubSub implements BeforeEachCallback, AfterEachCallback {

    private final Jedis client;

    private final String channel;

    private final Queue<String> messages = new LinkedList<>();

    private Thread subscriberThread;

    private long messageCount = 0;

    RedisSubscriberExtension(Jedis client, String channel) {
        this.client = client;
        this.channel = channel;
    }

    @Override
    public synchronized void beforeEach(ExtensionContext context) {
        final String threadName = RedisSubscriberExtension.class.getSimpleName();
        final Runnable threadTask = () -> client.subscribe(this, channel);
        subscriberThread = new Thread(threadTask, threadName);
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    @Override
    public synchronized void afterEach(ExtensionContext context) {
        unsubscribe();
        try {
            subscriberThread.join();
        } catch (InterruptedException error) {
            // Restore the interrupted flag
            Thread.currentThread().interrupt();
            StatusLogger.getLogger().error("interrupted while waiting for the subscriber thread termination", error);
        }
        messages.clear();
        messageCount = 0;
        subscriberThread = null;
    }

    @Override
    public synchronized void onMessage(final String channel, final String message) {
        if (this.channel.equals(channel)) {
            messageCount++;
            messages.add(message);
        }
    }

    synchronized long messageCount() {
        return messageCount;
    }

    synchronized String nextMessage() {
        return messages.poll();
    }

}
