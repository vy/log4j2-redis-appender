package com.vlkan.log4j2.redis.appender;

import java.net.URI;
import java.net.URISyntaxException;

enum TestHelpers {;

    static URI uri(String uri) {
        try {
            return new URI(uri);
        } catch (URISyntaxException error) {
            throw new RuntimeException("failed finding Log4j config", error);
        }
    }

}
