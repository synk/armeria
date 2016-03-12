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

import java.time.Duration;

import org.junit.Test;

public class CircuitBreakerTest {

    private static final String remoteServiceName = "testservice";

    private static final TestClock clock = new TestClock();

    private static final Duration circuitOpenWindow = Duration.ofSeconds(1);

    private static final Duration trialRequestInterval = Duration.ofSeconds(1);

    private static final Duration counterUpdateInterval = Duration.ofSeconds(1);

    private static CircuitBreaker create(long minimumRequestThreshold, double failureRateThreshold) {
        CircuitBreakerConfig config = new CircuitBreakerConfigBuilder(remoteServiceName)
                .failureRateThreshold(failureRateThreshold)
                .minimumRequestThreshold(minimumRequestThreshold)
                .circuitOpenWindow(circuitOpenWindow)
                .trialRequestInterval(trialRequestInterval)
                .counterSlidingWindow(Duration.ofSeconds(10))
                .counterUpdateInterval(counterUpdateInterval)
                .clock(clock)
                .build();

        return new CircuitBreaker(remoteServiceName, config);
    }

    private static CircuitBreaker closedState(long minimumRequestThreshold, double failureRateThreshold) {
        CircuitBreaker cb = create(minimumRequestThreshold, failureRateThreshold);
        assertThat(cb.getState().isClosed(), is(true));
        assertThat(cb.canRequest(), is(true));
        return cb;
    }

    private static CircuitBreaker openState(long minimumRequestThreshold, double failureRateThreshold) {
        CircuitBreaker cb = create(minimumRequestThreshold, failureRateThreshold);
        cb.onSuccess();
        cb.onFailure();
        cb.onFailure();
        clock.forward(counterUpdateInterval);
        cb.onFailure();
        assertThat(cb.getState().isOpen(), is(true));
        assertThat(cb.canRequest(), is(false));
        return cb;
    }

    private static CircuitBreaker halfOpenState(long minimumRequestThreshold, double failureRateThreshold) {
        CircuitBreaker cb = openState(minimumRequestThreshold, failureRateThreshold);

        clock.forward(circuitOpenWindow);

        assertThat(cb.getState().isHalfOpen(), is(false));
        assertThat(cb.canRequest(), is(true)); // first request is allowed
        assertThat(cb.getState().isHalfOpen(), is(true));
        assertThat(cb.canRequest(), is(false)); // seconds request is refused
        return cb;
    }

    @Test
    public void testClosed() {
        closedState(2, 0.5);
    }

    @Test
    public void testMinimumRequestThreshold() {
        CircuitBreaker cb = create(4, 0.5);
        assertThat(cb.getState().isClosed() && cb.canRequest(), is(true));

        cb.onFailure();
        clock.forward(counterUpdateInterval);
        cb.onFailure();
        assertThat(cb.getState().isClosed(), is(true));
        assertThat(cb.canRequest(), is(true));

        cb.onFailure();
        cb.onFailure();
        clock.forward(counterUpdateInterval);
        cb.onFailure();

        assertThat(cb.getState().isOpen(), is(true));
        assertThat(cb.canRequest(), is(false));
    }

    @Test
    public void testFailureRateThreshold() {
        CircuitBreaker cb = create(10, 0.5);

        for (int i = 0; i < 10; i++) {
            cb.onSuccess();
        }
        for (int i = 0; i < 9; i++) {
            cb.onFailure();
        }

        clock.forward(counterUpdateInterval);
        cb.onFailure();

        assertThat(cb.getState().isClosed(), is(true)); // 10 vs 9 (0.47)
        assertThat(cb.canRequest(), is(true));

        clock.forward(counterUpdateInterval);
        cb.onFailure();

        assertThat(cb.getState().isClosed(), is(true)); // 10 vs 10 (0.5)
        assertThat(cb.canRequest(), is(true));

        clock.forward(counterUpdateInterval);
        cb.onFailure();

        assertThat(cb.getState().isOpen(), is(true)); // 10 vs 11 (0.52)
        assertThat(cb.canRequest(), is(false));
    }

    @Test
    public void testClosedToOpen() {
        openState(2, 0.5);
    }

    @Test
    public void testOpenToHalfOpen() {
        halfOpenState(2, 0.5);
    }

    @Test
    public void testHalfOpenToClosed() {
        CircuitBreaker cb = halfOpenState(2, 0.5);

        cb.onSuccess();

        assertThat(cb.getState().isClosed(), is(true));
        assertThat(cb.canRequest(), is(true));
    }

    @Test
    public void testHalfOpenToOpen() {
        CircuitBreaker cb = halfOpenState(2, 0.5);

        cb.onFailure();

        assertThat(cb.getState().isOpen(), is(true));
        assertThat(cb.canRequest(), is(false));
    }

    @Test
    public void testHalfOpenRetryRequest() {
        CircuitBreaker cb = halfOpenState(2, 0.5);

        clock.forward(trialRequestInterval);

        assertThat(cb.getState().isHalfOpen(), is(true));
        assertThat(cb.canRequest(), is(true)); // first request is allowed
        assertThat(cb.getState().isHalfOpen(), is(true));
        assertThat(cb.canRequest(), is(false)); // seconds request is refused
    }
}
