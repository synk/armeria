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

import java.time.Duration;

/**
 * Stores configurations of circuit breaker.
 */
public final class CircuitBreakerConfig {

    /**
     * A policy of circuit breaker scoping.
     */
    public enum Scope {
        /**
         * sharing one circuit breaker among all methods of the remote service.
         */
        SERVICE,
        /**
         * binding one circuit breaker per method.
         */
        PER_METHOD
    }

    private final String remoteServiceName;

    private final double failureRateThreshold;

    private final Scope scope;

    private final Clock clock;

    private final FailureFilter failureFilter;

    private final long minimumRequestThreshold;

    private final Duration trialRequestInterval;

    private final Duration circuitOpenWindow;

    private final Duration counterSlidingWindow;

    private final Duration counterUpdateInterval;

    CircuitBreakerConfig(String remoteServiceName, double failureRateThreshold, Scope scope, Clock clock,
                         FailureFilter failureFilter, long minimumRequestThreshold,
                         Duration trialRequestInterval, Duration circuitOpenWindow,
                         Duration counterSlidingWindow, Duration counterUpdateInterval) {
        this.remoteServiceName = remoteServiceName;
        this.failureRateThreshold = failureRateThreshold;
        this.scope = scope;
        this.clock = clock;
        this.failureFilter = failureFilter;
        this.minimumRequestThreshold = minimumRequestThreshold;
        this.trialRequestInterval = trialRequestInterval;
        this.circuitOpenWindow = circuitOpenWindow;
        this.counterSlidingWindow = counterSlidingWindow;
        this.counterUpdateInterval = counterUpdateInterval;
    }

    public String remoteServiceName() {
        return remoteServiceName;
    }

    public Scope scope() {
        return scope;
    }

    public FailureFilter failureFilter() {
        return failureFilter;
    }

    public long minimumRequestThreshold() {
        return minimumRequestThreshold;
    }

    public double failureRateThreshold() {
        return failureRateThreshold;
    }

    public Duration circuitOpenWindow() {
        return circuitOpenWindow;
    }

    public Duration trialRequestInterval() {
        return trialRequestInterval;
    }

    public Duration counterSlidingWindow() {
        return counterSlidingWindow;
    }

    public Duration counterUpdateInterval() {
        return counterUpdateInterval;
    }

    Clock clock() {
        return clock;
    }

    @Override
    public String toString() {
        return "CircuitBreakerConfig{" +
               "remoteServiceName='" + remoteServiceName + '\'' +
               ", failureRateThreshold=" + failureRateThreshold +
               ", scope=" + scope +
               ", minimumRequestThreshold=" + minimumRequestThreshold +
               ", trialRequestInterval=" + trialRequestInterval +
               ", circuitOpenWindow=" + circuitOpenWindow +
               ", counterSlidingWindow=" + counterSlidingWindow +
               ", counterUpdateInterval=" + counterUpdateInterval +
               '}';
    }

}
