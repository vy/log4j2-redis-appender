/*
 * Copyright 2017-2022 Volkan Yazıcı
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

import java.net.URI;

/**
 * Constants of {@code log4j2.RedisAppenderSentinelTest.xml} configuration file.
 */
enum RedisAppenderSentinelTestConfig {;

    static final String REDIS_KEY = "log4j2-messages";

    static final String REDIS_HOST = "localhost";

    static final String REDIS_PASSWORD = "milonga";

    static final int REDIS_PORT = 63790;

    static final int REDIS_SENTINEL_PORT = 63791;

    static final String REDIS_SENTINEL_MASTER_NAME = "tango";

    static final URI LOG4J2_CONFIG_FILE_URI = URI.create("classpath:log4j2.RedisAppenderSentinelTest.xml");

    static final String LOG4J2_APPENDER_NAME = "REDIS";

}
