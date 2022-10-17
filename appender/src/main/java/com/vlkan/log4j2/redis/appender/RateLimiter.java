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

import io.github.resilience4j.ratelimiter.RateLimiterConfig;

import java.time.Duration;

abstract class RateLimiter {

    abstract boolean tryAcquire();

    abstract boolean tryAcquire(int permitCount);

    static final class Resilience4jRateLimiter extends RateLimiter {

        io.github.resilience4j.ratelimiter.RateLimiter delegate;

        private Resilience4jRateLimiter(io.github.resilience4j.ratelimiter.RateLimiter delegate) {
            this.delegate = delegate;
        }

        io.github.resilience4j.ratelimiter.RateLimiter getDelegate() {
            return delegate;
        }

        @Override
        boolean tryAcquire() {
            return delegate.acquirePermission();
        }

        @Override
        boolean tryAcquire(int permitCount) {
            return delegate.acquirePermission(permitCount);
        }

    }

    static RateLimiter ofMaxPermitCountPerSecond(String name, double maxPermitCountPerSecond) {

        // Check arguments.
        if (Double.isNaN(maxPermitCountPerSecond)) {
            throw new IllegalArgumentException("`maxPermitCountPerSecond` cannot be NaN");
        }
        if (Double.compare(0D, maxPermitCountPerSecond) >= 0) {
            throw new IllegalArgumentException(
                    "was expecting `maxPermitCountPerSecond` to be greater than zero, found: " +
                            maxPermitCountPerSecond);
        }

        // Convert the floating-point number to its cycle representation.
        String s = String.format("%f", maxPermitCountPerSecond).replaceAll("0+$", "");
        int i = s.indexOf('.');
        long cyclePeriodNanos = 1_000_000_000L;
        int maxPermitCountPerCycle;
        if (i < 0) {
            maxPermitCountPerCycle = Integer.parseInt(s);
        } else {
            for (int k = i + 1; k < s.length(); k++) {
                cyclePeriodNanos = Math.multiplyExact(10, cyclePeriodNanos);
            }
            maxPermitCountPerCycle = Integer.parseInt(s.replaceFirst("\\.", ""));
        }

        // Create the rate limiter.
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig
                .custom()
                .limitRefreshPeriod(Duration.ofNanos(cyclePeriodNanos))
                .limitForPeriod(maxPermitCountPerCycle)
                // Don't block on `acquirePermission()` calls:
                .timeoutDuration(Duration.ZERO)
                .writableStackTraceEnabled(false)
                .build();
        io.github.resilience4j.ratelimiter.RateLimiter rateLimiter =
                io.github.resilience4j.ratelimiter.RateLimiter.of(name, rateLimiterConfig);
        return new Resilience4jRateLimiter(rateLimiter);

    }

}
