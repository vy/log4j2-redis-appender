package com.vlkan.log4j2.redis.appender;

enum Helpers {;

    static void requireArgument(boolean condition, String messageFormat, Object... messageArguments) {
        if (!condition) {
            String message = String.format(messageFormat, messageArguments);
            throw new IllegalArgumentException(message);
        }
    }

}
