package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.core.util.datetime.FastDateFormat;

class DebugLogger {

    private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss.SSS");

    private final Class<?> parentClass;

    private final String parentClassName;

    private volatile boolean enabled;

    DebugLogger(Class<?> parentClass) {
        this(parentClass, true);
    }

    DebugLogger(Class<?> parentClass, boolean enabled) {
        this.parentClass = parentClass;
        this.parentClassName = parentClass.getSimpleName();
        this.enabled = enabled;
    }

    public void debug(String message, Object... messageArguments) {
        if (enabled) {
            String timestamp = DATE_FORMAT.format(System.currentTimeMillis());
            String threadName = Thread.currentThread().getName();
            String prefix = String.format("%s %s [%s] DEBUG", timestamp, threadName, parentClassName);
            String formattedMessage = String.format(prefix + ' ' + message, messageArguments);
            System.err.println(formattedMessage);
        }
    }

    public Class<?> getParentClass() {
        return parentClass;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return String.format("DebugLogger{parentClass=%s, enabled=%s}", parentClass, enabled);
    }

}
