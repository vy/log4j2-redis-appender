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
