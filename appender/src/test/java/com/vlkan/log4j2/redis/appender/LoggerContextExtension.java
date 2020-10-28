package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.URI;

class LoggerContextExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private final URI configFileUri;

    private LoggerContext loggerContext;

    LoggerContextExtension(URI configFileUri) {
        this.configFileUri = configFileUri;
    }

    synchronized LoggerContext getLoggerContext() {
        return loggerContext;
    }

    @Override
    public synchronized void beforeEach(ExtensionContext ignored) {
        LOGGER.debug("starting logger context (configFileUri='{}')", configFileUri);
        loggerContext = (LoggerContext) LogManager.getContext(
                ClassLoader.getSystemClassLoader(), false, configFileUri);
    }

    @Override
    public synchronized void afterEach(ExtensionContext ignored) {
        LOGGER.debug("stopping logger context (configFileUri='{}')", configFileUri);
        loggerContext.stop();
    }

    @Override
    public String toString() {
        return String.format("LoggerContextExtension{configFileUri='%s'}", configFileUri);
    }

}
