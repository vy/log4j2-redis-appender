[![Build Status](https://secure.travis-ci.org/vy/log4j2-redis-appender.svg)](http://travis-ci.org/vy/hrrs)
[![Maven Central](https://img.shields.io/maven-central/v/com.vlkan.log4j2/log4j2-redis-appender-parent.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.log4j2%22)
[![License](https://img.shields.io/github/license/vy/log4j2-redis-appender.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

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
            <RedisThrottlerConfig bufferSize="500"
                                  batchSize="100"
                                  flushPeriodMillis="1000"
                                  maxEventCountPerSecond="100"
                                  maxByteCountPerSecond="4194304"/>
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
| `RedisThrottlerConfig` | RedisThrottlerConfig | | Redis throttler configuration |
| `debugEnabled` | boolean | `false` | enables logging to `stderr` for debugging the plugin |

## Redis Connection Pool

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

## Redis Throttler

While Log4j 2 provides utilities like
[BurstFilter](https://logging.apache.org/log4j/2.x/manual/filters.html#BurstFilter)
and [AsyncAppender](https://logging.apache.org/log4j/2.x/manual/appenders.html#AsyncAppender)
that you can wrap around any appender to facilitate throttling,
[the appender API](https://logging.apache.org/log4j/2.0/log4j-core/apidocs/org/apache/logging/log4j/core/Appender.html)
[falls short of communicating this intent](http://mail-archives.apache.org/mod_mbox/logging-dev/201706.mbox/browser).
Hence, `RedisAppender` provides its own throttling mechanics to exploit batch
pushes available in [Redis RPUSH](https://redis.io/commands/rpush). This
feature is configured by `RedisThrottlerConfig` element using the following
attributes:

| Parameter Name | Type | Description |
|----------------|------|-------------|
| `bufferSize` | int | `LogEvent` buffer size (defaults to 500) |
| `batchSize` | int | size of batches fed into Redis `RPUSH` (defaults to 100) |
| `flushPeriodMillis` | long | buffer flush period (defaults to 1000) |
| `maxEventCountPerSecond` | double | allowed maximum number of events per second (defaults to 0, that is, unlimited) |
| `maxByteCountPerSecond` | double | allowed maximum number of bytes per second (defaults to 0, that is, unlimited) |
| `jmxBeanName` | String | `RedisThrottlerJmxBean` name (defaults to `org.apache.logging.log4j2:type=<loggerContextName>,component=Appenders,name=<appenderName>,subtype=RedisThrottler`) |

The buffer is flushed if either there are more than `batchSize` events
queued in the buffer or the last flush was older than `flushPeriodMillis`.

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

- **How can I avoid getting `AccessControlException` exceptions?** If you are
  using the plugin in a security manager enabled Java application (for
  instance, which is the case for Elasticsearch since version 2.3), you
  might be getting `AccessControlException` exceptions as follows:

  ```
  [2017-06-23T11:25:35,644][WARN ][o.e.b.ElasticsearchUncaughtExceptionHandler] [tst-store-001.data] uncaught exception in thread [commons-pool-EvictionTimer]
  java.security.AccessControlException: access denied ("java.lang.RuntimePermission" "setContextClassLoader")
          at java.security.AccessControlContext.checkPermission(AccessControlContext.java:472) ~[?:1.8.0_131]
          at java.security.AccessController.checkPermission(AccessController.java:884) ~[?:1.8.0_131]
          at java.lang.SecurityManager.checkPermission(SecurityManager.java:549) ~[?:1.8.0_131]
          at java.lang.Thread.setContextClassLoader(Thread.java:1474) [?:1.8.0_131]
          at org.apache.commons.pool2.impl.BaseGenericObjectPool$Evictor.run(BaseGenericObjectPool.java:1052) ~[log4j2-redis-appender.jar:?]
          at java.util.TimerThread.mainLoop(Timer.java:555) ~[?:1.8.0_131]
          at java.util.TimerThread.run(Timer.java:505) ~[?:1.8.0_131]
  ```

  To alleviate this, you need to grant necessary permissions using a
  [policy file](http://docs.oracle.com/javase/8/docs/technotes/guides/security/PolicyFiles.html):

  ```
  grant {
      permission java.lang.RuntimePermission "setContextClassLoader";
  };
  ```

  Then you can activate this policy for your application via either placing it
  under one of [default policy file locations](http://docs.oracle.com/javase/8/docs/technotes/guides/security/PolicyFiles.html#DefaultLocs)
  (e.g., `$JAVA_HOME/lib/security/java.policy`) or providing it as an argument
  at runtime, that is, `-Djava.security.policy=someURL`.

- **How can I access JMX bean of an appender?** Once you have a reference to
  the relevant `LoggerContext`, you can access the instance of the appender
  and its JMX bean by its name as follows:

  ```java
  Appender appender = loggerContext.getConfiguration().getAppender("REDIS");
  RedisThrottlerJmxBean jmxBean = ((RedisAppender) appender).getJmxBean();
  ```

  You can either create your own `LoggerContext`:

  ```java
  LoggerContextResource loggerContextResource = new LoggerContextResource("/path/to/log4j2.xml");
  LoggerContext loggerContext = loggerContextResource.getLoggerContext();
  ```

  or get a handle to an existing one:

  ```java
   LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
   ```

   Here note that you should be using `org.apache.logging.log4j.core.LoggerContext`,
   not `org.apache.logging.log4j.spi.LoggerContext`.

Contributors
============

- [Thomas van Kampen](https://github.com/t9t)
- [Yaroslav Skopets](https://github.com/yskopets)

# License

Copyright &copy; 2017-2018 [Volkan Yazıcı](http://vlkan.com/)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
