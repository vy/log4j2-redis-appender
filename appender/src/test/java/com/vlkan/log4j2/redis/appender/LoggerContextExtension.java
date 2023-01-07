/*
 * Copyright 2017-2023 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */
package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.status.StatusLogger;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.function.Consumer;

class LoggerContextExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private static final String LOGGER_PREFIX = String.format("[%s]", LoggerContextExtension.class.getSimpleName());

    private final Configuration config;

    private final LoggerContext loggerContext;

    LoggerContextExtension(
            String prefix,
            String rootAppenderName,
            Consumer<ConfigurationBuilder<BuiltConfiguration>> configCustomizer) {
        this.config = createConfig(prefix, rootAppenderName, configCustomizer);
        this.loggerContext = createLoggerContext(prefix);
    }

    private static Configuration createConfig(
            String prefix,
            String rootAppenderName,
            Consumer<ConfigurationBuilder<BuiltConfiguration>> configCustomizer) {

        // Create the configuration builder.
        String configName = String.format("%s-Config", prefix);
        ConfigurationBuilder<BuiltConfiguration> configBuilder = ConfigurationBuilderFactory
                .newConfigurationBuilder()
                .setStatusLevel(Level.ALL)
                .setConfigurationName(configName);

        // Create the configuration.
        configCustomizer.accept(configBuilder);

        // Set the root logger.
        configBuilder.add(configBuilder
                .newRootLogger(Level.ALL)
                .add(configBuilder.newAppenderRef(rootAppenderName)));

        // Create the configuration.
        return configBuilder.build(false);

    }

    private static LoggerContext createLoggerContext(String prefix) {
        String name = String.format("%s-LoggerContext", prefix);
        return new LoggerContext(name);
    }

    Configuration getConfig() {
        return config;
    }

    LoggerContext getLoggerContext() {
        return loggerContext;
    }

    @Override
    public synchronized void beforeEach(ExtensionContext ignored) {

        // Setting the logger context here rather than while creating the `LoggerContext` instance, since it implicitly triggers a configuration initialization.
        LOGGER.debug("{} setting the logger context configuration", LOGGER_PREFIX);
        loggerContext.reconfigure(config);

        // Mark the start.
        LOGGER.debug("{} started", LOGGER_PREFIX);

    }

    @Override
    public synchronized void afterEach(ExtensionContext ignored) {

        // Stop the logger context.
        LOGGER.debug("{} stopping the logger context", LOGGER_PREFIX);
        loggerContext.stop();

        // Stop the configuration.
        LOGGER.debug("{} stopping the configuration", LOGGER_PREFIX);
        config.stop();

        // Mark the completion.
        LOGGER.debug("{} stopped", LOGGER_PREFIX);

    }

}
