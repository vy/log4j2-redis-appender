package com.vlkan.log4j2.redis.appender;

import java.net.URI;

/**
 * Constants of {@code log4j2.RedisAppenderSentinelTest.xml} configuration file.
 */
enum RedisAppenderSentinelTestConfig {;

    static final String REDIS_KEY = "log4j2-messages";

    static final String REDIS_HOST = "localhost";

    static final String REDIS_PASSWORD = "toosecret";

    static final int REDIS_PORT = 63790;

    static final int REDIS_SENTINEL_PORT = 63791;

    static final String REDIS_SENTINEL_MASTER_NAME = "mymaster";

    static final URI LOG4J2_CONFIG_FILE_URI = TestHelpers.uri("classpath:log4j2.RedisAppenderSentinelTest.xml");

    static final String LOG4J2_APPENDER_NAME = "REDIS";

}
