package com.vlkan.log4j2.redis.appender;

import java.net.URI;

/**
 * Constants of {@code log4j2.RedisAppenderTest.xml} configuration file.
 */
enum RedisAppenderTestConfig {;

    static final String REDIS_KEY = "log4j2-messages";

    static final String REDIS_HOST = "localhost";

    static final String REDIS_PASSWORD = "milonga";

    static final int REDIS_PORT = 63790;

    static final URI LOG4J2_CONFIG_FILE_URI = URI.create("classpath:log4j2.RedisAppenderTest.xml");

    static final String LOG4J2_APPENDER_NAME = "REDIS";

}
