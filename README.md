[![Build Status](https://secure.travis-ci.org/vy/log4j2-redis-appender.svg)](http://travis-ci.org/vy/hrrs)
[![Maven Central](https://img.shields.io/maven-central/v/com.vlkan.log4j2/log4j2-redis-appender-parent.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.log4j2%22)

`RedisAppender` plugin provides a [Log4j 2.x](https://logging.apache.org/log4j/2.x/)
appender for [Redis](https://redis.io/) in-memory data structure store. The plugin
uses [Jedis](https://github.com/xetorthio/jedis) as a client for Redis.

# Usage

Add the `log4j2-redis-appender` dependency to your POM file

```xml
<dependency>
    <groupId>com.vlkan.log4j2</groupId>
    <artifactId>log4j2-redis-appender</artifactId>
    <version>${log4j2-redis-appender.version}</version>
</dependency>
```

together with a valid `log4j-core` dependency:

```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>${log4j2.version}</version>
</dependency>
```

Below you can find a sample `log4j2.xml` snippet employing `RedisAppender`.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration name="RedisAppenderTest">
    <Appenders>
        <RedisAppender name="REDIS"
                       key="log4j2-messages"
                       host="localhost"
                       port="6379">
            <PatternLayout pattern="%level %msg"/>
            <RedisConnectionPoolConfig testWhileIdle="true"
                                       minEvictableIdleTimeMillis="60000"
                                       timeBetweenEvictionRunsMillis="30000"
                                       numTestsPerEvictionRun="-1"/>
        </RedisAppender>
    </Appenders>
    <Loggers>
        <Root level="all">
            <AppenderRef ref="REDIS"/>
        </Root>
    </Loggers>
</Configuration>
```

`RedisAppender` is configured with the following parameters:

| Parameter Name | Type | Default | Description |
|----------------|------|---------|-------------|
| `charset` | String | `UTF-8` | output charset |
| `key` | String | | Redis queue key |
| `host` | String | `localhost` | Redis host|
| `port` | int | 6379 | Redis port |
| `password` | String | `null` | Redis password |
| `connectionTimeoutSeconds` | int | 2 | initial connection timeout in seconds |
| `socketTimeoutSeconds` | int | 2 | socket timeout in seconds |
| `ignoreExceptions` | boolean | `true` | Enabling causes exceptions encountered while appending events to be internally logged and then ignored. When set to false, exceptions will be propagated to the caller, instead. You must set this to false when wrapping this appender in a `FailoverAppender`. |
| `Layout` | Layout | `PatternLayout` | used to format the `LogEvent`s |
| `RedisConnectionPoolConfig` | RedisConnectionPoolConfig | | Redis connection pool configuration |
| `debugEnabled` | boolean | `false` | enables logging to `stderr` for debugging the plugin |

`RedisConnectionPoolConfig` is a wrapper for `JedisPoolConfig` which extends
[GenericObjectPoolConfig](https://commons.apache.org/proper/commons-pool/apidocs/org/apache/commons/pool2/impl/GenericObjectPoolConfig.html)
of [Apache Commons Pool](https://commons.apache.org/proper/commons-pool/).
Below is a complete list of available `RedisConnectionPoolConfig` attributes.

| Parameter Name | Type | Default |
|----------------|------|-------------|
| `maxTotal` | int | 8 |
| `maxIdle` | int | 8 |
| `minIdle` | int | 0 |
| `lifo` | boolean | `true` |
| `fairness` | boolean | `false` |
| `maxWaitMillis` | long | -1 |
| `minEvictableIdleTimeMillis` | long | 1000 * 60 |
| `softMinEvictableIdleTimeMillis` | long | -1 |
| `numTestsPerEvictionRun` | int | -1 |
| `testOnCreate` | boolean | `false` |
| `testOnBorrow` | boolean | `false` |
| `testOnReturn` | boolean | `false` |
| `testWhileIdle` | boolean | `true` |
| `timeBetweenEvictionRunsMillis` | long | 1000 * 30 |
| `evictionPolicyClassName` | String | `org.apache.commons.pool2.impl.DefaultEvictionPolicy` |
| `blockWhenExhausted` | boolean | `true` |
| `jmxEnabled` | boolean | `true` |
| `jmxNameBase` | String | `null` |
| `jmxNamePrefix` | String | `com.vlkan.log4j2.redis.appender.JedisConnectionPool` |

Fat JAR
=======

Project also contains a `log4j2-redis-appender-fatjar` artifact which
includes all its transitive dependencies in a separate shaded package (to
avoid the JAR Hell) with the exception of `log4j-core`, that you need to
include separately.

This might come handy if you want to use this plugin along with already
compiled applications, e.g., Elasticsearch 5.x, which requires Log4j 2.x.

F.A.Q.
======

- **How can I connect to multiple Redis servers for failover?** You can define
  multiple Redis appenders nested under a
  [FailoverAppender](https://logging.apache.org/log4j/2.x/manual/appenders.html#FailoverAppender).
  (Don't forget to turn off `ignoreExceptions` flag.)

- **How can I handle bursts?** See [BurstFilter](https://logging.apache.org/log4j/2.x/manual/filters.html#BurstFilter).

- **How can I buffer writes to Redis?** See [AsyncAppender](https://logging.apache.org/log4j/2.x/manual/appenders.html#AsyncAppender).

# License

Copyright &copy; 2017 [Volkan Yazıcı](http://vlkan.com/)

log4j2-redis-appender is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by the Free
Software Foundation, either version 3 of the License, or (at your option) any
later version.

log4j2-redis-appender is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
details.

You should have received a copy of the GNU General Public License along with
this program. If not, see http://www.gnu.org/licenses/.
