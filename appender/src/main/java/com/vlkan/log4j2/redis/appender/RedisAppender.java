package com.vlkan.log4j2.redis.appender;

import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.DefaultErrorHandler;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.util.Strings;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.UUID;

@Plugin(name = "RedisAppender",
        category = Core.CATEGORY_NAME,
        elementType = Appender.ELEMENT_TYPE,
        printObject = true)
public class RedisAppender implements Appender {

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<RedisAppender> {

        @PluginBuilderAttribute
        @Required(message = "missing name")
        private String name;

        @PluginBuilderAttribute
        private Charset charset = Charset.forName("UTF-8");

        @PluginElement("Layout")
        @Required(message = "missing layout")
        private Layout<? extends Serializable> layout = PatternLayout.newBuilder().withCharset(charset).build();

        @PluginBuilderAttribute
        @Required(message = "missing key")
        private String key;

        @PluginBuilderAttribute
        @Required(message = "missing host")
        private String host = "localhost";

        @PluginBuilderAttribute
        @Required(message = "missing port")
        private int port = 6379;

        @PluginBuilderAttribute
        private int connectionTimeoutSeconds = Protocol.DEFAULT_TIMEOUT;

        @PluginBuilderAttribute
        private int socketTimeoutSeconds = Protocol.DEFAULT_TIMEOUT;

        @PluginBuilderAttribute
        private boolean ignoreExceptions = true;

        private Builder() {
            // Do nothing.
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

        @Override
        public RedisAppender build() {
            check();
            return new RedisAppender(this);
        }

        private void check() {
            checkArgument(Strings.isNotBlank(name), "blank name");
            checkNotNull(charset, "charset");
            checkNotNull(layout, "layout");
            checkArgument(Strings.isNotBlank(key), "blank key");
            checkArgument(Strings.isNotBlank(host), "blank host");
            checkArgument(port > 0, "expecting: port > 0, found: %d", port);
            checkArgument(connectionTimeoutSeconds > 0, "expecting: connectionTimeoutSeconds > 0, found: %d", connectionTimeoutSeconds);
            checkArgument(socketTimeoutSeconds > 0, "expecting: socketTimeoutSeconds > 0, found: %d", socketTimeoutSeconds);
        }

        private static void checkNotNull(Object instance, String name) {
            if (instance == null) {
                throw new NullPointerException(name);
            }
        }

        private static void checkArgument(boolean condition, String messageFormat, Object... messageArguments) {
            if (!condition) {
                String message = String.format(messageFormat, messageArguments);
                throw new IllegalArgumentException(message);
            }
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
                    '}';
        }

    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    private volatile Jedis jedis;

    private volatile State state;

    private volatile ErrorHandler errorHandler = new DefaultErrorHandler(this);

    private final String name;

    private final Layout<? extends Serializable> layout;

    private final String key;

    private final byte[] keyBytes;

    private final String host;

    private final int port;

    private final int connectionTimeoutSeconds;

    private final int socketTimeoutSeconds;

    private final boolean ignoreExceptions;

    private RedisAppender(Builder builder) {
        this.name = builder.name;
        this.layout = builder.layout;
        this.key = builder.key;
        this.keyBytes = builder.key.getBytes(builder.charset);
        this.host = builder.host;
        this.port = builder.port;
        this.connectionTimeoutSeconds = builder.connectionTimeoutSeconds;
        this.socketTimeoutSeconds = builder.socketTimeoutSeconds;
        this.ignoreExceptions = builder.ignoreExceptions;
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

    @Override
    public void append(LogEvent event) {
        if (State.STARTED.equals(state)) {
            byte[] eventBytes = layout.toByteArray(event);
            try {
                jedis.rpush(keyBytes, eventBytes);
            } catch (JedisConnectionException error) {

                // Try reconnecting.
                synchronized (this) {
                    if (!isHealthy()) {
                        disconnect();
                        connect();
                    }
                }

                // Check health again and propagate the error, if necessary. (Required by FailoverAppender.)
                if (!isHealthy() && !ignoreExceptions) {
                    throw error;
                }

            }
        }
    }

    private boolean isHealthy() {
        String input = String.format("%s health check (nonce=%s)", RedisAppender.class.getCanonicalName(), UUID.randomUUID());
        try {
            String output = jedis.echo(input);
            return input.equals(output);
        } catch (JedisConnectionException ignored) {
            return false;
        }
    }

    @Override
    public void initialize() {
        changeState(null, State.INITIALIZING, State.INITIALIZED, new Runnable() {
            @Override
            public void run() {
                int connectionTimeoutMillis = 1_000 * connectionTimeoutSeconds;
                int socketTimeoutMillis = 1_000 * socketTimeoutSeconds;
                jedis = new Jedis(host, port, connectionTimeoutMillis, socketTimeoutMillis);
            }
        });
    }

    @Override
    public void start() {
        ensureInitialized();
        changeState(State.INITIALIZED, State.STARTING, State.STARTED, new Runnable() {
            @Override
            public void run() {
                connect();
            }
        });
    }

    private synchronized void ensureInitialized() {
        if (state == null) {
            initialize();
        }
    }

    private synchronized void changeState(State initialState, State transitionState, State finalState, Runnable body) {
        if (initialState != state) {
            String message = String.format("expecting: %s, found: %s", initialState, state);
            errorHandler.error(message);
            throw new IllegalStateException(message);
        }
        state = transitionState;
        try {
            if (body != null) {
                body.run();
            }
            state = finalState;
        } catch (Exception error) {
            String message = String.format(
                    "state change failure (initialState=%s, transitionState=%s, finalState=%s)",
                    initialState, transitionState, finalState);
            errorHandler.error(message, error);
            throw new RuntimeException(message, error);
        }
    }

    @Override
    public synchronized void stop() {
        state = State.STOPPING;
        if (jedis != null && jedis.isConnected()) {
            disconnect();
        }
        state = State.STOPPED;
    }

    private void connect() {
        jedis.connect();
    }

    private void disconnect() {
        try {
            jedis.disconnect();
        } catch (JedisConnectionException ignored) {
            // Ignore connection issues.
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
        return "RedisAppender{" + "state=" + state +
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

}
