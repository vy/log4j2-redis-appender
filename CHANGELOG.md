<!---
 Copyright 2017-2022 Volkan Yazıcı

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permits and
 limitations under the License.
-->

### (????-??-??) v0.13.0

- Fixed `NumberFormatException` thrown by `RateLimiter.ofMaxPermitCountPerSecond()` when the system locale formats floating point numbers in an unexpected way

### (2022-12-12) v0.12.0

- Replaced `it.ozimov:embedded-redis` with `org.signal:embedded-redis` (#22)

- Replaced hardcoded Guava rate limiter with Resilience4j (#23)

- Switched to programmatic `LoggerContext` creation in tests (#24)

- Relocated all the packages to `com.vlkan.log4j2.redis.appender.*` in the fat JAR, otherwise they were (rightfully) causing `Found multiple occurrences of org.json.JSONObject on the class path` errors

### (2022-10-04) v0.11.1

- Earlier throttler fix has introduced a bug. The buffer cursor is not reset correctly and caused duplicates in logged events. (Thanks to [Khaled Bakhtiari](https://github.com/ec84b4) for the report.)

### (2022-09-02) v0.11.0

- Flushed remaining events in the buffer after interrupting the throttler

- Added Redis logical database support (#16)

- Switched to [semantic versioning](https://semver.org/)

- Removed `debugEnabled` flag

### (2021-06-10) v0.10

- Added `maxErrorCountPerSecond` configuration to the throttler

### (2020-10-28) v0.9

- Flushed remaining events in the buffer after closing the throttler (#12)

- Switched to JUnit 5

- Replaced `com.github.kstyrc:embedded-redis:0.6` with `it.ozimov:embedded-redis:0.7.3`

- Refactored tests

- Deprecated `debugEnabled` flag and replace the custom logger with Log4j `StatusLogger`

### (2020-10-06) v0.8

- Added Redis [Sentinel](https://redis.io/topics/sentinel) support (#9)

### (2019-07-23) v0.7

- Removed Guava dependency by copying the `RateLimiter`

### (2019-06-07) v0.6

- Updated Jedis to 3.1.0-m4

### (2019-05-24) v0.5

- Bumped dependency versions

- Upgraded to Java 8 (#3)

- Marked flush trigger thread as daemon (#3)

- Switched to Apache License v2.0
