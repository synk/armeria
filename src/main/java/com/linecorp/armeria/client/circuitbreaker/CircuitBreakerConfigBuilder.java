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

import static java.util.Objects.requireNonNull;

import java.time.Duration;

import com.linecorp.armeria.client.circuitbreaker.CircuitBreakerConfig.Scope;

/**
 * Builds a {@link CircuitBreakerConfig} instance using builder pattern.
 */
public final class CircuitBreakerConfigBuilder {

    private static final double DEFAULT_FAILURE_RATE_THRESHOLD = 0.8;

    private static final Scope DEFAULT_SCOPE = Scope.SERVICE;

    private static final Clock DEFAULT_CLOCK = System::currentTimeMillis;

    private static final FailureFilter DEFAULT_FAILURE_FILTER = cause -> true;

    private static final long DEFAULT_MINIMUM_REQUEST_THRESHOLD = 10;

    private static final Duration DEFAULT_TRIAL_REQUEST_INTERVAL = Duration.ofSeconds(3);

    private static final Duration DEFAULT_CIRCUIT_OPEN_WINDOW = Duration.ofSeconds(10);

    private static final Duration DEFAULT_COUNTER_SLIDING_WINDOW = Duration.ofSeconds(20);

    private static final Duration DEFAULT_COUNTER_UPDATE_INTERVAL = Duration.ofSeconds(1);

    private final String remoteServiceName;

    private double failureRateThreshold = DEFAULT_FAILURE_RATE_THRESHOLD;

    private Scope scope = DEFAULT_SCOPE;

    private Clock clock = DEFAULT_CLOCK;

    private FailureFilter failureFilter = DEFAULT_FAILURE_FILTER;

    private long minimumRequestThreshold = DEFAULT_MINIMUM_REQUEST_THRESHOLD;

    private Duration trialRequestInterval = DEFAULT_TRIAL_REQUEST_INTERVAL;

    private Duration circuitOpenWindow = DEFAULT_CIRCUIT_OPEN_WINDOW;

    private Duration counterSlidingWindow = DEFAULT_COUNTER_SLIDING_WINDOW;

    private Duration counterUpdateInterval = DEFAULT_COUNTER_UPDATE_INTERVAL;

    /**
     * Creates a new {@link CircuitBreakerConfigBuilder} with the specified name of remote service.
     *
     * @param remoteServiceName The remote service name to be logged
     */
    public CircuitBreakerConfigBuilder(String remoteServiceName) {
        this.remoteServiceName = requireNonNull(remoteServiceName);
        if (remoteServiceName.isEmpty()) {
            throw new IllegalArgumentException("remoteServiceName must not be empty");
        }
    }

    /**
     * Sets the threshold of failure rate to detect a remote service fault.
     *
     * @param failureRateThreshold The rate between 0 (exclusive) and 1 (inclusive)
     */
    public CircuitBreakerConfigBuilder failureRateThreshold(double failureRateThreshold) {
        if (failureRateThreshold <= 0 || 1 < failureRateThreshold) {
            throw new IllegalArgumentException(
                    "failureRateThreshold must be between 0 (exclusive) and 1 (inclusive)");
        }
        this.failureRateThreshold = failureRateThreshold;
        return this;
    }

    /**
     * Sets the {@link Scope} indicating the policy of circuit breaker scoping.
     */
    public CircuitBreakerConfigBuilder scope(Scope scope) {
        this.scope = requireNonNull(scope, "scope");
        return this;
    }

    /**
     * Sets the minimum number of requests within a time window necessary to detect a remote service fault.
     */
    public CircuitBreakerConfigBuilder minimumRequestThreshold(long minimumRequestThreshold) {
        if (minimumRequestThreshold < 0) {
            throw new IllegalArgumentException("minimumRequestThreshold must be >= 0");
        }
        this.minimumRequestThreshold = minimumRequestThreshold;
        return this;
    }

    /**
     * Sets the trial request interval in HALF_OPEN state.
     */
    public CircuitBreakerConfigBuilder trialRequestInterval(Duration trialRequestInterval) {
        requireNonNull(trialRequestInterval, "trialRequestInterval");
        if (trialRequestInterval.isNegative() || trialRequestInterval.isZero()) {
            throw new IllegalArgumentException("trialRequestInterval must be greater than zero");
        }
        this.trialRequestInterval = trialRequestInterval;
        return this;
    }

    /**
     * Sets the duration of OPEN state.
     */
    public CircuitBreakerConfigBuilder circuitOpenWindow(Duration circuitOpenWindow) {
        requireNonNull(circuitOpenWindow, "circuitOpenWindow");
        if (circuitOpenWindow.isNegative() || circuitOpenWindow.isZero()) {
            throw new IllegalArgumentException("circuitOpenWindow must be greater than zero");
        }
        this.circuitOpenWindow = circuitOpenWindow;
        return this;
    }

    /**
     * Sets the time length of sliding window to accumulate the count of events.
     */
    public CircuitBreakerConfigBuilder counterSlidingWindow(Duration counterSlidingWindow) {
        requireNonNull(counterSlidingWindow, "counterSlidingWindow");
        if (counterSlidingWindow.isNegative() || counterSlidingWindow.isZero()) {
            throw new IllegalArgumentException("counterSlidingWindow must be greater than zero");
        }
        this.counterSlidingWindow = counterSlidingWindow;
        return this;
    }

    /**
     * Sets the interval that a circuit breaker can see the latest accumulated count of events.
     */
    public CircuitBreakerConfigBuilder counterUpdateInterval(Duration counterUpdateInterval) {
        requireNonNull(counterUpdateInterval, "counterUpdateInterval");
        if (counterUpdateInterval.isNegative() || counterUpdateInterval.isZero()) {
            throw new IllegalArgumentException("counterUpdateInterval must be greater than zero");
        }
        this.counterUpdateInterval = counterUpdateInterval;
        return this;
    }

    /**
     * Sets the {@link FailureFilter} that decides whether the circuit breaker should deal with a given error.
     */
    public CircuitBreakerConfigBuilder failureFilter(FailureFilter failureFilter) {
        this.failureFilter = requireNonNull(failureFilter, "failureFilter");
        return this;
    }

    /**
     * Sets the {@link Clock} to be used inside circuit breaker.
     */
    CircuitBreakerConfigBuilder clock(Clock clock) {
        this.clock = requireNonNull(clock, "clock");
        return this;
    }

    /**
     * Builds a {@link CircuitBreakerConfig} instance.
     */
    public CircuitBreakerConfig build() {
        if (counterSlidingWindow.compareTo(counterUpdateInterval) <= 0) {
            throw new IllegalArgumentException(
                    "counterSlidingWindow must be greater than counterUpdateInterval");
        }
        return new CircuitBreakerConfig(remoteServiceName, failureRateThreshold, scope, clock, failureFilter,
                                        minimumRequestThreshold, trialRequestInterval, circuitOpenWindow,
                                        counterSlidingWindow, counterUpdateInterval);
    }

}
