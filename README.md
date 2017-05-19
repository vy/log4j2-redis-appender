[![Build Status](https://secure.travis-ci.org/vy/log4j2-redis-appender.svg)](http://travis-ci.org/vy/hrrs)
[![Maven Central](https://img.shields.io/maven-central/v/com.vlkan.log4j2/log4j2-redis-appender-parent.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vlkan.log4j2%22)

`RedisAppender` plugin provides a [Log4j 2.x](https://logging.apache.org/log4j/2.x/)
appender for [Redis](https://redis.io/) in-memory data structure store.

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
        <RedisAppender name="REDIS" key="log4j2-messages" host="localhost" port="6379">
            <PatternLayout pattern="%level %msg"/>
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

| Parameter Name | Type | Description |
|----------------|------|-------------|
| `charset` | String | output charset (defaults to `UTF-8`) |
| `key` | String | Redis queue key |
| `host` | String | Redis host (defaults to `localhost`) |
| `port` | int | Redis port (defaults to 6379) |
| `connectionTimeoutSeconds` | int | initial connection timeout in seconds (defaults to 2) |
| `socketTimeoutSeconds` | int | socket timeout in seconds (defaults to 2) |
| `ignoreExceptions` | boolean | The default is true, causing exceptions encountered while appending events to be internally logged and then ignored. When set to false exceptions will be propagated to the caller, instead. You must set this to false when wrapping this appender in a `FailoverAppender`. |

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
