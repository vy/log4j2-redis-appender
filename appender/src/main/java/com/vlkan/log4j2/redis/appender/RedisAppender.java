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

    private final Configuration config;

    private final String name;

    private final Layout<? extends Serializable> layout;

    private final String key;

    private final byte[] keyBytes;

    private final String host;

    private final int port;

    private final String password;

    private final int connectionTimeoutSeconds;

    private final int socketTimeoutSeconds;

    private final boolean ignoreExceptions;

    private final boolean debugEnabled;

    private final String sentinelNodes;

    private final String sentinelMaster;

    private final RedisConnectionPoolConfig poolConfig;

    private final DebugLogger logger;

    private final RedisThrottler throttler;

    private volatile Pool<Jedis> jedisPool;

    private volatile State state;

    private volatile ErrorHandler errorHandler = new DefaultErrorHandler(this);

    private RedisAppender(Builder builder) {
        this.config = builder.config;
        this.name = builder.name;
        this.layout = builder.layout;
        this.key = builder.key;
        this.keyBytes = builder.key.getBytes(builder.charset);
        this.host = builder.host;
        this.port = builder.port;
        this.password = builder.password;
        this.connectionTimeoutSeconds = builder.connectionTimeoutSeconds;
        this.socketTimeoutSeconds = builder.socketTimeoutSeconds;
        this.ignoreExceptions = builder.ignoreExceptions;
        this.debugEnabled = builder.debugEnabled;
        this.sentinelNodes = builder.sentinelNodes;
        this.sentinelMaster = builder.sentinelMaster;
        this.poolConfig = builder.poolConfig;
        this.logger = new DebugLogger(RedisAppender.class, debugEnabled);
        this.throttler = new RedisThrottler(builder.getThrottlerConfig(), this, ignoreExceptions, debugEnabled);
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
        logger.debug("consuming %d events", events.length);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.rpush(keyBytes, events);
        }
    }

    public RedisThrottlerJmxBean getJmxBean() {
        return throttler.getJmxBean();
    }

    @Override
    public void append(LogEvent event) {
        logger.debug("appending: %s", event.getMessage().getFormattedMessage());
        if (State.STARTED.equals(state)) {
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
        logger.debug("starting");
        ensureInitialized();
        changeState(State.INITIALIZED, State.STARTING, State.STARTED, this::connect);
    }

    private synchronized void ensureInitialized() {
        if (state == null) {
            initialize();
        }
    }

    private synchronized void changeState(State initialState, State transitionState, State finalState, Runnable body) {
        logger.debug("expecting state: %s", initialState);
        if (initialState != state) {
            String message = String.format("expecting: %s, found: %s", initialState, state);
            errorHandler.error(message);
            throw new IllegalStateException(message);
        }
        logger.debug("transitioning state: %s", transitionState);
        state = transitionState;
        try {
            if (body != null) {
                body.run();
            }
            logger.debug("finalizing state: %s", finalState);
            state = finalState;
        } catch (Exception error) {
            String message = String.format(
                    "state change failure (initialState=%s, transitionState=%s, finalState=%s)",
                    initialState, transitionState, finalState);
            if (debugEnabled) {
                logger.debug("%s: %s", message, error.getMessage());
                error.printStackTrace();
            }
            errorHandler.error(message, error);
            throw new RuntimeException(message, error);
        }
    }

    @Override
    public synchronized void stop() {
        logger.debug("stopping");
        state = State.STOPPING;
        throttler.close();
        if (jedisPool != null && !jedisPool.isClosed()) {
            disconnect();
        }
        state = State.STOPPED;
    }

    private void connect() {
        logger.debug("connecting");
        int connectionTimeoutMillis = 1_000 * connectionTimeoutSeconds;
        int socketTimeoutMillis = 1_000 * socketTimeoutSeconds;
        boolean sentinel = isNotBlank(sentinelNodes);
        if (sentinel) {
            Set<String> sentinelNodesAsSet = Stream.of(sentinelNodes.split("\\s*,\\s*"))
                    .filter(Strings::isNotBlank)
                    .collect(Collectors.toSet());
            jedisPool = new JedisSentinelPool(
                    sentinelMaster,
                    sentinelNodesAsSet,
                    poolConfig.getJedisPoolConfig(),
                    connectionTimeoutMillis,
                    socketTimeoutMillis,
                    password,
                    0,          // database
                    null        // clientName
            );
        } else {
            jedisPool = new JedisPool(
                    poolConfig.getJedisPoolConfig(),
                    host,
                    port,
                    connectionTimeoutMillis,
                    socketTimeoutMillis,
                    password,
                    0,          // database
                    null,       // clientName
                    false,      // ssl
                    null,       // sslSocketFactory
                    null,       // sslParameters,
                    null       // hostnameVerifier
            );
        }
    }

    private void disconnect() {
        logger.debug("disconnecting");
        try {
            jedisPool.destroy();
        } catch (JedisConnectionException error) {
            logger.debug("disconnect failure: %s", error.getMessage());
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
        private Layout<? extends Serializable> layout = PatternLayout.newBuilder().withCharset(charset).build();

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
        private boolean debugEnabled = false;

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

        public Layout<? extends Serializable> getLayout() {
            return layout;
        }

        public Builder setLayout(Layout<LogEvent> layout) {
            this.layout = layout;
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

        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        public Builder setDebugEnabled(boolean debugEnabled) {
            this.debugEnabled = debugEnabled;
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
                    ", key='" + key + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", connectionTimeoutSeconds=" + connectionTimeoutSeconds +
                    ", socketTimeoutSeconds=" + socketTimeoutSeconds +
                    ", ignoreExceptions=" + ignoreExceptions +
                    ", debugEnabled=" + debugEnabled +
                    '}';
        }

    }

}
