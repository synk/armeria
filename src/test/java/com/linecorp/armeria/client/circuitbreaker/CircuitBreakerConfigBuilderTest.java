/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.client.circuitbreaker;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.time.Duration;

import org.junit.Test;

import com.linecorp.armeria.client.circuitbreaker.CircuitBreakerConfig.Scope;

public class CircuitBreakerConfigBuilderTest {

    private static final String remoteServiceName = "testservice";

    private static final Duration minusDuration = Duration.ZERO.minusMillis(1);

    private static final Duration oneSecond = Duration.ofSeconds(1);

    private static final Duration twoSeconds = Duration.ofSeconds(2);

    private static void throwsException(Runnable runnable) {
        try {
            runnable.run();
            fail();
        } catch (IllegalArgumentException | NullPointerException e) {
        }
    }

    private static CircuitBreakerConfigBuilder builder() {
        return new CircuitBreakerConfigBuilder(remoteServiceName);
    }

    @Test
    public void testConstructor() {
        CircuitBreakerConfig config = new CircuitBreakerConfigBuilder(remoteServiceName).build();
        assertThat(config.remoteServiceName(), is(remoteServiceName));
    }

    @Test
    public void testConstructorWithInvalidArgument() {
        throwsException(() -> new CircuitBreakerConfigBuilder(null));
        throwsException(() -> new CircuitBreakerConfigBuilder(""));
    }

    @Test
    public void testFailureRateThreshold() {
        assertThat(builder().failureRateThreshold(0.123).build().failureRateThreshold(), is(0.123));
        assertThat(builder().failureRateThreshold(1).build().failureRateThreshold(), is(1.0));
    }

    @Test
    public void testFailureRateThresholdWithInvalidArgument() {
        throwsException(() -> builder().failureRateThreshold(0));
        throwsException(() -> builder().failureRateThreshold(-1));
        throwsException(() -> builder().failureRateThreshold(1.1));
    }

    @Test
    public void testScope() {
        assertThat(builder().scope(Scope.PER_METHOD).build().scope(), is(Scope.PER_METHOD));
        assertThat(builder().scope(Scope.SERVICE).build().scope(), is(Scope.SERVICE));
    }

    @Test
    public void testScopeWithInvalidArgument() {
        throwsException(() -> builder().scope(null));
    }

    @Test
    public void testMinimumRequestThreshold() {
        CircuitBreakerConfig config1 = builder().minimumRequestThreshold(Long.MAX_VALUE).build();
        assertThat(config1.minimumRequestThreshold(), is(Long.MAX_VALUE));

        CircuitBreakerConfig config2 = builder().minimumRequestThreshold(0).build();
        assertThat(config2.minimumRequestThreshold(), is(0L));
    }

    @Test
    public void testMinimumRequestThresholdWithInvalidArgument() {
        throwsException(() -> builder().minimumRequestThreshold(-1));
    }

    @Test
    public void testTrialRequestInterval() {
        CircuitBreakerConfig config = builder().trialRequestInterval(oneSecond).build();
        assertThat(config.trialRequestInterval(), is(oneSecond));
    }

    @Test
    public void testTrialRequestIntervalWithInvalidArgument() {
        throwsException(() -> builder().trialRequestInterval(null));
        throwsException(() -> builder().trialRequestInterval(Duration.ZERO));
        throwsException(() -> builder().trialRequestInterval(minusDuration));
    }

    @Test
    public void testCircuitOpenWindow() {
        CircuitBreakerConfig config = builder().circuitOpenWindow(oneSecond).build();
        assertThat(config.circuitOpenWindow(), is(oneSecond));
    }

    @Test
    public void testCircuitOpenWindowWithInvalidArgument() {
        throwsException(() -> builder().circuitOpenWindow(null));
        throwsException(() -> builder().circuitOpenWindow(Duration.ZERO));
        throwsException(() -> builder().circuitOpenWindow(minusDuration));
    }

    @Test
    public void testCounterSlidingWindow() {
        CircuitBreakerConfig config = builder()
                .counterSlidingWindow(twoSeconds)
                .counterUpdateInterval(oneSecond)
                .build();
        assertThat(config.counterSlidingWindow(), is(twoSeconds));
    }

    @Test
    public void testCounterSlidingWindowWithInvalidArgument() {
        throwsException(() -> builder().counterSlidingWindow(null));
        throwsException(() -> builder().counterSlidingWindow(Duration.ZERO));
        throwsException(() -> builder().counterSlidingWindow(minusDuration));

        throwsException(() -> builder().counterSlidingWindow(oneSecond).counterUpdateInterval(twoSeconds)
                                       .build());
    }

    @Test
    public void testCounterUpdateInterval() {
        CircuitBreakerConfig config = builder()
                .counterSlidingWindow(twoSeconds)
                .counterUpdateInterval(oneSecond)
                .build();
        assertThat(config.counterUpdateInterval(), is(oneSecond));
    }

    @Test
    public void testCounterUpdateIntervalWithInvalidArgument() {
        throwsException(() -> builder().counterUpdateInterval(null));
        throwsException(() -> builder().counterUpdateInterval(Duration.ZERO));
        throwsException(() -> builder().counterUpdateInterval(minusDuration));
    }

    @Test
    public void testFailureFilter() {
        FailureFilter instance = e -> true;
        assertThat(builder().failureFilter(instance).build().failureFilter(), is(instance));
    }

    @Test
    public void testFailureFilterWithInvalidArgument() {
        throwsException(() -> builder().failureFilter(null));
    }

    @Test
    public void testClock() {
        Clock clock = () -> Long.MAX_VALUE;
        CircuitBreakerConfig config = builder().clock(clock).build();
        assertThat(config.clock(), is(clock));
    }

    @Test
    public void testClockWithInvalidArgument() {
        throwsException(() -> builder().clock(null));
    }

}
