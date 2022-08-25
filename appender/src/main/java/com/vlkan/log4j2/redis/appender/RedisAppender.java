/*
 * Copyright 2017-2022 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */
package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.ErrorHandler;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.DefaultErrorHandler;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSentinelPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.util.Pool;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.vlkan.log4j2.redis.appender.Helpers.requireArgument;
import static java.util.Objects.requireNonNull;
import static org.apache.logging.log4j.util.Strings.isNotBlank;

@Plugin(name = "RedisAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE,
        printObject = true)
public class RedisAppender implements Appender {

    private static final StatusLogger LOGGER = StatusLogger.getLogger();

    private final Configuration config;

    private final String name;

    private final String logPrefix;

    private final Layout<?> layout;

    private final int database;

    private final String key;

    private final byte[] keyBytes;

    private final String host;

    private final int port;

    private final String password;

    private final int connectionTimeoutSeconds;

    private final int socketTimeoutSeconds;

    private final boolean ignoreExceptions;

    private final String sentinelNodes;

    private final String sentinelMaster;

    private final RedisConnectionPoolConfig poolConfig;

    private final RedisThrottler throttler;

    private volatile Pool<Jedis> jedisPool;

    private volatile State state;

    private volatile ErrorHandler errorHandler = new DefaultErrorHandler(this);

    private RedisAppender(Builder builder) {
        this.config = builder.config;
        this.name = builder.name;
        this.logPrefix = String.format("[RedisAppender{%s}]", builder.name);
        this.layout = builder.layout;
        this.database = builder.database;
        this.key = builder.key;
        this.keyBytes = builder.key.getBytes(builder.charset);
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.connectionTimeoutSeconds = builder.connectionTimeoutSeconds;
        this.socketTimeoutSeconds = builder.socketTimeoutSeconds;
        this.ignoreExceptions = builder.ignoreExceptions;
        this.sentinelNodes = builder.sentinelNodes;
        this.sentinelMaster = builder.sentinelMaster;
        this.poolConfig = builder.poolConfig;
        this.throttler = new RedisThrottler(builder.getThrottlerConfig(), this, ignoreExceptions);
    }

    public Configuration getConfig() {
        return config;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Layout<? extends Serializable> getLayout() {
        return layout;
    }

    @Override
    public boolean ignoreExceptions() {
        return ignoreExceptions;
    }

    @Override
    public ErrorHandler getHandler() {
        return errorHandler;
    }

    @Override
    public void setHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public State getState() {
        return state;
    }

    void consumeThrottledEvents(byte[]... events) {
        LOGGER.debug("{} consuming {} events", logPrefix, events.length);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush(keyBytes, events);
        }
    }

    public RedisThrottlerJmxBean getJmxBean() {
        return throttler.getJmxBean();
    }

    @Override
    public void append(LogEvent event) {
        if (State.STARTED.equals(state)) {
            LOGGER.debug("{} appending: {}", logPrefix, event.getMessage().getFormattedMessage());
            byte[] eventBytes = layout.toByteArray(event);
            throttler.push(eventBytes);
        }
    }

    @Override
    public void initialize() {
        changeState(null, State.INITIALIZING, State.INITIALIZED, throttler::start);
    }

    @Override
    public void start() {
        LOGGER.info("{} starting", logPrefix);
        ensureInitialized();
        changeState(State.INITIALIZED, State.STARTING, State.STARTED, this::connect);
    }

    private synchronized void ensureInitialized() {
        if (state == null) {
            initialize();
        }
    }

    private synchronized void changeState(State initialState, State transitionState, State finalState, Runnable body) {
        LOGGER.trace("{} expecting state: {}", logPrefix, initialState);
        if (initialState != state) {
            String message = String.format("expecting: %s, found: %s", initialState, state);
            errorHandler.error(message);
            throw new IllegalStateException(message);
        }
        LOGGER.debug("{} transitioning state: {}", logPrefix, transitionState);
        state = transitionState;
        try {
            if (body != null) {
                body.run();
            }
            LOGGER.debug("{} finalizing state: {}", logPrefix, finalState);
            state = finalState;
        } catch (Exception error) {
            String message = String.format(
                    "state change failure (initialState=%s, transitionState=%s, finalState=%s)",
                    initialState, transitionState, finalState);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(logPrefix + " " + message, error);
            }
            errorHandler.error(message, error);
            throw new RuntimeException(message, error);
        }
    }

    @Override
    public synchronized void stop() {
        LOGGER.info("{} stopping", logPrefix);
        state = State.STOPPING;
        throttler.close();
        if (jedisPool != null && !jedisPool.isClosed()) {
            disconnect();
        }
        state = State.STOPPED;
    }

    private void connect() {
        LOGGER.debug("{} connecting", logPrefix);
        int connectionTimeoutMillis = 1_000 * connectionTimeoutSeconds;
        int socketTimeoutMillis = 1_000 * socketTimeoutSeconds;
        boolean sentinel = isNotBlank(sentinelNodes);
        if (sentinel) {
            Set<String> sentinelNodesAsSet = Stream
                    .of(sentinelNodes.split("\\s*,\\s*"))
                    .filter(Strings::isNotBlank)
                    .collect(Collectors.toSet());
            jedisPool = new JedisSentinelPool(
                    sentinelMaster,
                    sentinelNodesAsSet,
                    poolConfig.getJedisPoolConfig(),
                    connectionTimeoutMillis,
                    socketTimeoutMillis,
                    password,
                    database,
                    null);      // clientName
        } else {
            jedisPool = new JedisPool(
                    poolConfig.getJedisPoolConfig(),
                    host,
                    port,
                    connectionTimeoutMillis,
                    socketTimeoutMillis,
                    password,
                    database,
                    null,       // clientName
                    false,      // ssl
                    null,       // sslSocketFactory
                    null,       // sslParameters,
                    null);      // hostnameVerifier
        }
    }

    private void disconnect() {
        LOGGER.debug("{} disconnecting", logPrefix);
        try {
            jedisPool.destroy();
        } catch (JedisConnectionException error) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn(logPrefix + " disconnect failure", error);
            }
        } finally {
            jedisPool = null;
        }
    }

    @Override
    public boolean isStarted() {
        return state == State.STARTED;
    }

    @Override
    public boolean isStopped() {
        return state == State.STOPPED;
    }

    @Override
    public String toString() {
        return "RedisAppender{state=" + state +
                ", name='" + name + '\'' +
                ", layout='" + layout + '\'' +
                ", database=" + database +
                ", key='" + key + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", connectionTimeoutSeconds=" + connectionTimeoutSeconds +
                ", socketTimeoutSeconds=" + socketTimeoutSeconds +
                ", ignoreExceptions=" + ignoreExceptions +
                '}';
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<RedisAppender> {

        @PluginConfiguration
        private Configuration config;

        @PluginBuilderAttribute
        @Required(message = "missing name")
        private String name;

        @PluginBuilderAttribute
        private Charset charset = StandardCharsets.UTF_8;

        @PluginElement("Layout")
        private Layout<?> layout = PatternLayout.newBuilder().withCharset(charset).build();

        @PluginBuilderAttribute
        private int database = 0;

        @PluginBuilderAttribute
        @Required(message = "missing key")
        private String key;

        @PluginBuilderAttribute
        private String host = "localhost";

        @PluginBuilderAttribute
        private String password = null;

        @PluginBuilderAttribute
        private int port = 6379;

        @PluginBuilderAttribute
        private int connectionTimeoutSeconds = Protocol.DEFAULT_TIMEOUT;

        @PluginBuilderAttribute
        private int socketTimeoutSeconds = Protocol.DEFAULT_TIMEOUT;

        @PluginBuilderAttribute
        private boolean ignoreExceptions = true;

        @PluginBuilderAttribute
        private String sentinelNodes;

        @PluginBuilderAttribute
        private String sentinelMaster;

        @PluginElement("RedisConnectionPoolConfig")
        private RedisConnectionPoolConfig poolConfig = RedisConnectionPoolConfig.newBuilder().build();

        @PluginElement("RedisThrottlerConfig")
        private RedisThrottlerConfig throttlerConfig = RedisThrottlerConfig.newBuilder().build();

        private Builder() {
            // Do nothing.
        }

        public Configuration getConfig() {
            return config;
        }

        public Builder setConfig(Configuration config) {
            this.config = config;
            return this;
        }

        public String getName() {
            return name;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Charset getCharset() {
            return charset;
        }

        public Builder setCharset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Layout<?> getLayout() {
            return layout;
        }

        public Builder setLayout(Layout<?> layout) {
            this.layout = layout;
            return this;
        }

        public int getDatabase() {
            return database;
        }

        public Builder setDatabase(int database) {
            this.database = database;
            return this;
        }

        public String getKey() {
            return key;
        }

        public Builder setKey(String key) {
            this.key = key;
            return this;
        }

        public String getHost() {
            return host;
        }

        public Builder setHost(String host) {
            this.host = host;
            return this;
        }

        public int getPort() {
            return port;
        }

        public Builder setPort(int port) {
            this.port = port;
            return this;
        }

        public String getPassword() {
            return password;
        }

        public Builder setPassword(String password) {
            this.password = password;
            return this;
        }

        public int getConnectionTimeoutSeconds() {
            return connectionTimeoutSeconds;
        }

        public Builder setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
            return this;
        }

        public int getSocketTimeoutSeconds() {
            return socketTimeoutSeconds;
        }

        public Builder setSocketTimeoutSeconds(int socketTimeoutSeconds) {
            this.socketTimeoutSeconds = socketTimeoutSeconds;
            return this;
        }

        public boolean isIgnoreExceptions() {
            return ignoreExceptions;
        }

        public Builder setIgnoreExceptions(boolean ignoreExceptions) {
            this.ignoreExceptions = ignoreExceptions;
            return this;
        }

        public String getSentinelNodes() {
            return sentinelNodes;
        }

        public Builder setSentinelNodes(String sentinelNodes) {
            this.sentinelNodes = sentinelNodes;
            return this;
        }

        public String getSentinelMaster() {
            return sentinelMaster;
        }

        public Builder setSentinelMaster(String sentinelMaster) {
            this.sentinelMaster = sentinelMaster;
            return this;
        }

        public RedisConnectionPoolConfig getPoolConfig() {
            return poolConfig;
        }

        public Builder setPoolConfig(RedisConnectionPoolConfig poolConfig) {
            this.poolConfig = poolConfig;
            return this;
        }

        public RedisThrottlerConfig getThrottlerConfig() {
            return throttlerConfig;
        }

        public Builder setThrottlerConfig(RedisThrottlerConfig throttlerConfig) {
            this.throttlerConfig = throttlerConfig;
            return this;
        }

        @Override
        public RedisAppender build() {
            check();
            return new RedisAppender(this);
        }

        private void check() {
            requireNonNull(config, "config");
            requireArgument(Strings.isNotBlank(name), "blank name");
            requireNonNull(charset, "charset");
            requireNonNull(layout, "layout");
            requireArgument(database >= 0, "expecting: database >= 0, found: %d", database);
            requireArgument(Strings.isNotBlank(key), "blank key");
            requireArgument(Strings.isNotBlank(host), "blank host");
            requireArgument(port > 0, "expecting: port > 0, found: %d", port);
            if (sentinelNodes != null) {
                requireArgument(Strings.isNotBlank(sentinelNodes), "blank sentinel nodes");
                requireArgument(Strings.isNotBlank(sentinelMaster), "blank sentinel master");
            }
            requireArgument(connectionTimeoutSeconds > 0, "expecting: connectionTimeoutSeconds > 0, found: %d", connectionTimeoutSeconds);
            requireArgument(socketTimeoutSeconds > 0, "expecting: socketTimeoutSeconds > 0, found: %d", socketTimeoutSeconds);
            requireNonNull(poolConfig, "poolConfig");
            requireNonNull(throttlerConfig, "throttlerConfig");
        }

        @Override
        public String toString() {
            return "Builder{name='" + name + '\'' +
                    ", charset=" + charset +
                    ", layout='" + layout + '\'' +
                    ", database=" + database +
                    ", key='" + key + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", connectionTimeoutSeconds=" + connectionTimeoutSeconds +
                    ", socketTimeoutSeconds=" + socketTimeoutSeconds +
                    ", ignoreExceptions=" + ignoreExceptions +
                    '}';
        }

    }

}
