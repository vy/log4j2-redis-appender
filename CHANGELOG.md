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

### (2022-09-02) v0.11.0

- Flush remaining events in the buffer after interrupting the throttler.

- Add Redis logical database support. (#16)

- Switched to [semantic versioning](https://semver.org/).

- Removed `debugEnabled` flag.

### (2021-06-10) v0.10

- Add `maxErrorCountPerSecond` configuration to the throttler.

### (2020-10-28) v0.9

- Flush remaining events in the buffer after closing the throttler. (#12)

- Switch to JUnit 5.

- Replace `com.github.kstyrc:embedded-redis:0.6` with
  `it.ozimov:embedded-redis:0.7.3`.

- Refactor tests.

- Deprecate `debugEnabled` flag and replace the custom logger with
  Log4j `StatusLogger`.

### (2020-10-06) v0.8

- Added Redis [Sentinel](https://redis.io/topics/sentinel) support. (#9)

### (2019-07-23) v0.7

- Remove Guava dependency by copying the `RateLimiter`.

### (2019-06-07) v0.6

- Update Jedis to 3.1.0-m4.

### (2019-05-24) v0.5

- Bump dependency versions.

- Upgrade to Java 8. (#3)

- Mark flush trigger thread as daemon. (#3)

- Switch to Apache License v2.0.
