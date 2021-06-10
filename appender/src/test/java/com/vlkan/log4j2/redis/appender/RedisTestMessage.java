/*
 * Copyright 2017-2021 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */
package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.Level;

import java.util.concurrent.atomic.AtomicInteger;

import static com.vlkan.log4j2.redis.appender.RedisTestConstants.RANDOM;

class RedisTestMessage {

    private static final Level[] LEVELS = Level.values();

    private static final int MIN_MESSAGE_LENGTH = 10;

    private static final int MAX_MESSAGE_LENGTH = 100;

    final Level level;

    final String message;

    private static AtomicInteger COUNTER = new AtomicInteger(0);

    private RedisTestMessage(Level level, String message) {
        this.level = level;
        this.message = message;
    }

    private static RedisTestMessage createRandom() {
        int levelIndex = RANDOM.nextInt(LEVELS.length);
        Level level = LEVELS[levelIndex];
        int messageLength = MIN_MESSAGE_LENGTH + RANDOM.nextInt(MAX_MESSAGE_LENGTH - MIN_MESSAGE_LENGTH);
        String prefix = String.format("[%d] ", COUNTER.getAndIncrement());
        StringBuilder messageBuilder = new StringBuilder(prefix);
        while (messageBuilder.length() < messageLength) {
            char messageChar = (char) RANDOM.nextInt(Character.MAX_VALUE);
            if (Character.isLetterOrDigit(messageChar)) {
                messageBuilder.append(messageChar);
            }
        }
        String message = messageBuilder.toString();
        return new RedisTestMessage(level, message);
    }

    static RedisTestMessage[] createRandomArray(int count) {
        RedisTestMessage[] messages = new RedisTestMessage[count];
        for (int i = 0; i < count; i++) {
            messages[i] = createRandom();
        }
        return messages;
    }

}
