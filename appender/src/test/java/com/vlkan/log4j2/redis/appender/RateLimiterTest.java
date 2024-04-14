/*
 * Copyright 2017-2024 Volkan Yazıcı
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

import com.vlkan.log4j2.redis.appender.RateLimiter.Resilience4jRateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.Locale;

class RateLimiterTest {

    @ParameterizedTest
    @MethodSource("com.vlkan.log4j2.redis.appender.RateLimiterTest#maxPermitCountPerSecondTestCases")
    void ofMaxPermitCountPerSecond_should_convert_correctly(
            double maxPermitCountPerSecond,
            long cyclePeriodSeconds,
            long maxPermitCountPerCycle) {

        // Create the rate limiter.
        RateLimiter rateLimiter = RateLimiter.ofMaxPermitCountPerSecond("test", maxPermitCountPerSecond);

        // Extract the Resilience4j rate limiter config.
        Assertions.assertThat(rateLimiter).isInstanceOf(Resilience4jRateLimiter.class);
        Resilience4jRateLimiter resilience4jRateLimiter = (Resilience4jRateLimiter) rateLimiter;
        RateLimiterConfig rateLimiterConfig = resilience4jRateLimiter.getDelegate().getRateLimiterConfig();

        // Verify the config.
        long expectedCyclePeriodNanos = Math.multiplyExact(1_000_000_000L, cyclePeriodSeconds);
        Duration expectedCyclePeriod = Duration.ofNanos(expectedCyclePeriodNanos);
        Assertions.assertThat(rateLimiterConfig.getLimitRefreshPeriod()).isEqualTo(expectedCyclePeriod);
        Assertions.assertThat(rateLimiterConfig.getLimitForPeriod()).isEqualTo(maxPermitCountPerCycle);

    }

    static Object[][] maxPermitCountPerSecondTestCases() {
        return new Object[][] {
                // 1. maxPermitCountPerSecond
                // 2. expectedCyclePeriodSeconds
                // 3. expectedMaxPermitCountPerCycle
                {100D,         1L,    100},
                { 10D,         1L,     10},
                {  1D,         1L,      1},
                {  1.0D,       1L,      1},
                {  1.00D,      1L,      1},
                {  1.000D,     1L,      1},
                {  0.1D,      10L,      1},
                {  0.01D,    100L,      1},
                {  0.001D,  1000L,      1}
        };
    }

    @Test
    @ResourceLock(value = Resources.LOCALE, mode = ResourceAccessMode.READ_WRITE)
    void ofMaxPermitCountPerSecond_should_convert_independent_of_locale() {

        // Find a locale that formats floats in an unexpected way
        Locale newLocale = Locale.forLanguageTag("nl-NL");
        Assertions.assertThat(String.format(newLocale, "%f", Math.PI)).doesNotMatch("^\\d+\\.\\d+$");

        // Use this odd locale to see if `ofMaxPermitCountPerSecond()` will fail
        Locale oldLocale = Locale.getDefault();
        try {
            Locale.setDefault(newLocale);
            Assertions
                    .assertThatCode(() -> RateLimiter.ofMaxPermitCountPerSecond("test", 100))
                    .doesNotThrowAnyException();
        } finally {
            Locale.setDefault(oldLocale);
        }

    }

}
