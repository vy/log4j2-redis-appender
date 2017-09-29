package com.vlkan.log4j2.redis.appender;

interface RedisThrottlerReceiver {

    void consumeThrottledEvents(byte[]... events) throws Exception;

}
