package com.vlkan.log4j2.redis.appender;

interface RedisThrottlerReceiver {

    void consumeThrottledEvent(byte[] event) throws Exception;

    void consumeThrottledEvents(byte[]... events) throws Exception;

}
