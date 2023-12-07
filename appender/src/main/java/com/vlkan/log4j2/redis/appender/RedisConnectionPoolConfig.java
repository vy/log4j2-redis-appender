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

import org.apache.commons.pool2.impl.BaseObjectPoolConfig;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

@Plugin(name = "RedisConnectionPoolConfig",
        category = Node.CATEGORY,
        printObject = true)
public class RedisConnectionPoolConfig {

    private final int maxTotal;

    private final int maxIdle;

    private final int minIdle;

    private final boolean lifo;

    private final boolean fairness;

    private final long maxWaitMillis;

    private final long minEvictableIdleTimeMillis;

    private final long softMinEvictableIdleTimeMillis;

    private final int numTestsPerEvictionRun;

    private final boolean testOnCreate;

    private final boolean testOnBorrow;

    private final boolean testOnReturn;

    private final boolean testWhileIdle;

    private final long timeBetweenEvictionRunsMillis;

    private final String evictionPolicyClassName;

    private final boolean blockWhenExhausted;

    private final boolean jmxEnabled;

    private final String jmxNameBase;

    private final String jmxNamePrefix;

    private final JedisPoolConfig jedisPoolConfig;

    private RedisConnectionPoolConfig(Builder builder) {
        this.maxTotal = builder.maxTotal;
        this.maxIdle = builder.maxIdle;
        this.minIdle = builder.minIdle;
        this.lifo = builder.lifo;
        this.fairness = builder.fairness;
        this.maxWaitMillis = builder.maxWaitMillis;
        this.minEvictableIdleTimeMillis = builder.minEvictableIdleTimeMillis;
        this.softMinEvictableIdleTimeMillis = builder.softMinEvictableIdleTimeMillis;
        this.numTestsPerEvictionRun = builder.numTestsPerEvictionRun;
        this.testOnCreate = builder.testOnCreate;
        this.testOnBorrow = builder.testOnBorrow;
        this.testOnReturn = builder.testOnReturn;
        this.testWhileIdle = builder.testWhileIdle;
        this.timeBetweenEvictionRunsMillis = builder.timeBetweenEvictionRunsMillis;
        this.evictionPolicyClassName = builder.evictionPolicyClassName;
        this.blockWhenExhausted = builder.blockWhenExhausted;
        this.jmxEnabled = builder.jmxEnabled;
        this.jmxNameBase = builder.jmxNameBase;
        this.jmxNamePrefix = builder.jmxNamePrefix;
        this.jedisPoolConfig = createJedisPoolConfig(builder);
    }

    private static JedisPoolConfig createJedisPoolConfig(Builder builder) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(builder.maxTotal);
        config.setMaxIdle(builder.maxIdle);
        config.setMinIdle(builder.minIdle);
        config.setLifo(builder.lifo);
        config.setFairness(builder.fairness);
        config.setMaxWait(Duration.ofMillis(builder.maxWaitMillis));
        config.setMinEvictableIdleDuration(Duration.ofMillis(builder.minEvictableIdleTimeMillis));
        config.setSoftMinEvictableIdleDuration(Duration.ofMillis(builder.softMinEvictableIdleTimeMillis));
        config.setNumTestsPerEvictionRun(builder.numTestsPerEvictionRun);
        config.setTestOnCreate(builder.testOnCreate);
        config.setTestOnBorrow(builder.testOnBorrow);
        config.setTestOnReturn(builder.testOnReturn);
        config.setTestWhileIdle(builder.testWhileIdle);
        config.setTimeBetweenEvictionRuns(Duration.ofMillis(builder.timeBetweenEvictionRunsMillis));
        config.setEvictionPolicyClassName(builder.evictionPolicyClassName);
        config.setBlockWhenExhausted(builder.blockWhenExhausted);
        config.setJmxEnabled(builder.jmxEnabled);
        config.setJmxNameBase(builder.jmxNameBase);
        config.setJmxNamePrefix(builder.jmxNamePrefix);
        return config;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public boolean isLifo() {
        return lifo;
    }

    public boolean isFairness() {
        return fairness;
    }

    public long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    public long getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    public long getSoftMinEvictableIdleTimeMillis() {
        return softMinEvictableIdleTimeMillis;
    }

    public int getNumTestsPerEvictionRun() {
        return numTestsPerEvictionRun;
    }

    public boolean isTestOnCreate() {
        return testOnCreate;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public boolean isTestOnReturn() {
        return testOnReturn;
    }

    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    public long getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    public String getEvictionPolicyClassName() {
        return evictionPolicyClassName;
    }

    public boolean isBlockWhenExhausted() {
        return blockWhenExhausted;
    }

    public boolean isJmxEnabled() {
        return jmxEnabled;
    }

    public String getJmxNameBase() {
        return jmxNameBase;
    }

    public String getJmxNamePrefix() {
        return jmxNamePrefix;
    }

    public JedisPoolConfig getJedisPoolConfig() {
        return jedisPoolConfig;
    }

    @Override
    public String toString() {
        return "Builder{maxTotal=" + maxTotal +
                ", maxIdle=" + maxIdle +
                ", minIdle=" + minIdle +
                ", lifo=" + lifo +
                ", fairness=" + fairness +
                ", maxWaitMillis=" + maxWaitMillis +
                ", minEvictableIdleTimeMillis=" + minEvictableIdleTimeMillis +
                ", softMinEvictableIdleTimeMillis=" + softMinEvictableIdleTimeMillis +
                ", numTestsPerEvictionRun=" + numTestsPerEvictionRun +
                ", testOnCreate=" + testOnCreate +
                ", testOnBorrow=" + testOnBorrow +
                ", testOnReturn=" + testOnReturn +
                ", testWhileIdle=" + testWhileIdle +
                ", timeBetweenEvictionRunsMillis=" + timeBetweenEvictionRunsMillis +
                ", evictionPolicyClassName='" + evictionPolicyClassName + '\'' +
                ", blockWhenExhausted=" + blockWhenExhausted +
                ", jmxEnabled=" + jmxEnabled +
                ", jmxNameBase='" + jmxNameBase + '\'' +
                ", jmxNamePrefix='" + jmxNamePrefix + '\'' +
                '}';
    }

    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder implements org.apache.logging.log4j.core.util.Builder<RedisConnectionPoolConfig> {

        @PluginBuilderAttribute
        private int maxTotal = 8;

        @PluginBuilderAttribute
        private int maxIdle = 8;

        @PluginBuilderAttribute
        private int minIdle = 0;

        @PluginBuilderAttribute
        private boolean lifo = true;

        @PluginBuilderAttribute
        private boolean fairness = false;

        @PluginBuilderAttribute
        private long maxWaitMillis = -1L;

        @PluginBuilderAttribute
        private long minEvictableIdleTimeMillis = 1000L * 60L;

        @PluginBuilderAttribute
        private long softMinEvictableIdleTimeMillis = -1;

        @PluginBuilderAttribute
        private int numTestsPerEvictionRun = -1;

        @PluginBuilderAttribute
        private boolean testOnCreate = false;

        @PluginBuilderAttribute
        private boolean testOnBorrow = false;

        @PluginBuilderAttribute
        private boolean testOnReturn = false;

        @PluginBuilderAttribute
        private boolean testWhileIdle = true;

        @PluginBuilderAttribute
        private long timeBetweenEvictionRunsMillis = 1000L * 30L;

        @PluginBuilderAttribute
        private String evictionPolicyClassName = BaseObjectPoolConfig.DEFAULT_EVICTION_POLICY_CLASS_NAME;

        @PluginBuilderAttribute
        private boolean blockWhenExhausted = true;

        @PluginBuilderAttribute
        private boolean jmxEnabled = true;

        @PluginBuilderAttribute
        private String jmxNameBase = null;

        @PluginBuilderAttribute
        private String jmxNamePrefix = "com.vlkan.log4j2.redis.appender.JedisConnectionPool";

        public int getMaxTotal() {
            return maxTotal;
        }

        public Builder setMaxTotal(int maxTotal) {
            this.maxTotal = maxTotal;
            return this;
        }

        public int getMaxIdle() {
            return maxIdle;
        }

        public Builder setMaxIdle(int maxIdle) {
            this.maxIdle = maxIdle;
            return this;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public Builder setMinIdle(int minIdle) {
            this.minIdle = minIdle;
            return this;
        }

        public boolean getLifo() {
            return lifo;
        }

        public Builder setLifo(boolean lifo) {
            this.lifo = lifo;
            return this;
        }

        public boolean getFairness() {
            return fairness;
        }

        public Builder setFairness(boolean fairness) {
            this.fairness = fairness;
            return this;
        }

        public long getMaxWaitMillis() {
            return maxWaitMillis;
        }

        public Builder setMaxWaitMillis(long maxWaitMillis) {
            this.maxWaitMillis = maxWaitMillis;
            return this;
        }

        public long getMinEvictableIdleTimeMillis() {
            return minEvictableIdleTimeMillis;
        }

        public Builder setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
            this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
            return this;
        }

        public long getSoftMinEvictableIdleTimeMillis() {
            return softMinEvictableIdleTimeMillis;
        }

        public Builder setSoftMinEvictableIdleTimeMillis(long softMinEvictableIdleTimeMillis) {
            this.softMinEvictableIdleTimeMillis = softMinEvictableIdleTimeMillis;
            return this;
        }

        public int getNumTestsPerEvictionRun() {
            return numTestsPerEvictionRun;
        }

        public Builder setNumTestsPerEvictionRun(int numTestsPerEvictionRun) {
            this.numTestsPerEvictionRun = numTestsPerEvictionRun;
            return this;
        }

        public boolean getTestOnCreate() {
            return testOnCreate;
        }

        public Builder setTestOnCreate(boolean testOnCreate) {
            this.testOnCreate = testOnCreate;
            return this;
        }

        public boolean getTestOnBorrow() {
            return testOnBorrow;
        }

        public Builder setTestOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
            return this;
        }

        public boolean getTestOnReturn() {
            return testOnReturn;
        }

        public Builder setTestOnReturn(boolean testOnReturn) {
            this.testOnReturn = testOnReturn;
            return this;
        }

        public boolean getTestWhileIdle() {
            return testWhileIdle;
        }

        public Builder setTestWhileIdle(boolean testWhileIdle) {
            this.testWhileIdle = testWhileIdle;
            return this;
        }

        public long getTimeBetweenEvictionRunsMillis() {
            return timeBetweenEvictionRunsMillis;
        }

        public Builder setTimeBetweenEvictionRunsMillis(long timeBetweenEvictionRunsMillis) {
            this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
            return this;
        }

        public String getEvictionPolicyClassName() {
            return evictionPolicyClassName;
        }

        public Builder setEvictionPolicyClassName(String evictionPolicyClassName) {
            this.evictionPolicyClassName = evictionPolicyClassName;
            return this;
        }

        public boolean getBlockWhenExhausted() {
            return blockWhenExhausted;
        }

        public Builder setBlockWhenExhausted(boolean blockWhenExhausted) {
            this.blockWhenExhausted = blockWhenExhausted;
            return this;
        }

        public boolean getJmxEnabled() {
            return jmxEnabled;
        }

        public Builder setJmxEnabled(boolean jmxEnabled) {
            this.jmxEnabled = jmxEnabled;
            return this;
        }

        public String getJmxNameBase() {
            return jmxNameBase;
        }

        public Builder setJmxNameBase(String jmxNameBase) {
            this.jmxNameBase = jmxNameBase;
            return this;
        }

        public String getJmxNamePrefix() {
            return jmxNamePrefix;
        }

        public Builder setJmxNamePrefix(String jmxNamePrefix) {
            this.jmxNamePrefix = jmxNamePrefix;
            return this;
        }

        private Builder() {
            // Do nothing.
        }

        public RedisConnectionPoolConfig build() {
            return new RedisConnectionPoolConfig(this);
        }

        @Override
        public String toString() {
            return "Builder{maxTotal=" + maxTotal +
                    ", maxIdle=" + maxIdle +
                    ", minIdle=" + minIdle +
                    ", lifo=" + lifo +
                    ", fairness=" + fairness +
                    ", maxWaitMillis=" + maxWaitMillis +
                    ", minEvictableIdleTimeMillis=" + minEvictableIdleTimeMillis +
                    ", softMinEvictableIdleTimeMillis=" + softMinEvictableIdleTimeMillis +
                    ", numTestsPerEvictionRun=" + numTestsPerEvictionRun +
                    ", testOnCreate=" + testOnCreate +
                    ", testOnBorrow=" + testOnBorrow +
                    ", testOnReturn=" + testOnReturn +
                    ", testWhileIdle=" + testWhileIdle +
                    ", timeBetweenEvictionRunsMillis=" + timeBetweenEvictionRunsMillis +
                    ", evictionPolicyClassName='" + evictionPolicyClassName + '\'' +
                    ", blockWhenExhausted=" + blockWhenExhausted +
                    ", jmxEnabled=" + jmxEnabled +
                    ", jmxNameBase='" + jmxNameBase + '\'' +
                    ", jmxNamePrefix='" + jmxNamePrefix + '\'' +
                    '}';
        }

    }

}
