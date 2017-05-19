package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.rules.ExternalResource;

import java.net.URI;

public class LoggerContextResource extends ExternalResource {

    private final URI configFileUri;

    private LoggerContext loggerContext;

    public LoggerContextResource(URI configFileUri) {
        this.configFileUri = configFileUri;
    }

    public URI getConfigFileUri() {
        return configFileUri;
    }

    public LoggerContext getLoggerContext() {
        return loggerContext;
    }

    @Override
    protected void before() throws Throwable {
        loggerContext = (LoggerContext) LogManager.getContext(ClassLoader.getSystemClassLoader(), false, configFileUri);
        loggerContext.start();
    }

    @Override
    protected void after() {
        loggerContext.stop();
    }

    @Override
    public String toString() {
        return String.format("LoggerContextRule{configFileUri='%s'}", configFileUri);
    }

}
