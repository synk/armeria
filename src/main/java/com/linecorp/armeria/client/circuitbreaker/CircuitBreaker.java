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
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A non-blocking implementation of circuit breaker pattern.
 */
class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private enum CircuitState {
        /**
         * Initial state. All requests are sent to the remote service.
         */
        CLOSED,
        /**
         * The circuit is tripped. All requests fail immediately without calling the remote service.
         */
        OPEN,
        /**
         * Only one trial request is sent at a time until at least one request succeeds or fails.
         * If it doesn't complete within a certain time, another trial request will be sent again.
         * All other requests fails immediately same as OPEN.
         */
        HALF_OPEN
    }

    private final String name;

    private final CircuitBreakerConfig config;

    private final AtomicReference<State> current;

    private final Clock clock;

    /**
     * Creates a new {@link CircuitBreaker} with the specified name and {@link CircuitBreakerConfig}.
     */
    CircuitBreaker(String name, CircuitBreakerConfig config) {
        this.name = requireNonNull(name, "name");
        this.config = requireNonNull(config, "config");
        clock = config.clock();
        current = new AtomicReference<>(newClosedState());
        logStateTransition(CircuitState.CLOSED, EventCount.ZERO);
    }

    void onSuccess() {
        final State state = current.get();
        if (state.isClosed()) {
            // fires success event
            state.counter().onSuccess();
        } else if (state.isHalfOpen()) {
            // changes to CLOSED if at least one request succeeds during HALF_OPEN
            if (current.compareAndSet(state, newClosedState())) {
                logStateTransition(CircuitState.CLOSED, EventCount.ZERO);
            }
        }
    }

    void onFailure() {
        final State state = current.get();
        if (state.isClosed()) {
            // fires failure event
            state.counter().onFailure();
            final EventCount count = state.counter().getCount();
            if (checkIfExceedingFailureThreshold(count)) {
                // changes to OPEN if failure rate exceeds the threshold
                if (current.compareAndSet(state, newOpenState())) {
                    logStateTransition(CircuitState.OPEN, count);
                }
            }
        } else if (state.isHalfOpen()) {
            // returns to OPEN if a request fails during HALF_OPEN
            if (current.compareAndSet(state, newOpenState())) {
                logStateTransition(CircuitState.OPEN, EventCount.ZERO);
            }
        }
    }

    private boolean checkIfExceedingFailureThreshold(EventCount count) {
        return config.minimumRequestThreshold() <= count.total() &&
               config.failureRateThreshold() < count.failureRate();
    }

    /**
     * Decides whether a request should be allowed or refused according to the current circuit state.
     */
    boolean canRequest() {
        final State state = current.get();
        if (state.isClosed()) {
            // all requests are allowed during CLOSED
            return true;
        } else if (state.isHalfOpen() || state.isOpen()) {
            if (state.checkTimeout() && current.compareAndSet(state, newHalfOpenState())) {
                // changes to HALF_OPEN if OPEN state has timed out
                logStateTransition(CircuitState.HALF_OPEN, EventCount.ZERO);
                return true;
            }
            // all other requests are refused
            return false;
        }
        return true;
    }

    private State newOpenState() {
        return new State(CircuitState.OPEN, config.circuitOpenWindow(), NoOpCounter.INSTANCE);
    }

    private State newHalfOpenState() {
        return new State(CircuitState.HALF_OPEN, config.trialRequestInterval(), NoOpCounter.INSTANCE);
    }

    private State newClosedState() {
        return new State(CircuitState.CLOSED, Duration.ZERO, new SlidingWindowCounter(config));
    }

    private void logStateTransition(CircuitState circuitState, EventCount count) {
        if (logger.isInfoEnabled()) {
            final int capacity = name.length() + circuitState.name().length() + 32;
            final StringBuilder builder = new StringBuilder(capacity);
            builder.append("name:");
            builder.append(name);
            builder.append(" state:");
            builder.append(circuitState.name());
            if (EventCount.ZERO.equals(count)) {
                builder.append(" fail:- total:-");
            } else {
                builder.append(" fail:");
                builder.append(count.failure());
                builder.append(" total:");
                builder.append(count.total());
            }
            logger.info(builder.toString());
        }
    }

    // visible for testing
    State getState() {
        return current.get();
    }

    /**
     * A value object that stores the internal state of the circuit breaker.
     */
    class State {
        private final CircuitState circuitState;
        private final EventCounter counter;
        private final long startMillis;
        private final long timeoutDurationMillis;

        /**
         * Creates a new instance.
         *
         * @param circuitState The circuit state
         * @param timeout The max duration of the state
         * @param counter The event counter to use during the state
         */
        private State(CircuitState circuitState, Duration timeout, EventCounter counter) {
            this.circuitState = circuitState;
            this.counter = counter;
            this.startMillis = clock.currentMillis();
            this.timeoutDurationMillis = timeout.toMillis();
        }

        private EventCounter counter() {
            return counter;
        }

        /**
         * Returns {@code true} if this state has timed out.
         */
        private boolean checkTimeout() {
            return 0 < timeoutDurationMillis && startMillis + timeoutDurationMillis <= clock.currentMillis();
        }

        boolean isOpen() {
            return circuitState == CircuitState.OPEN;
        }

        boolean isHalfOpen() {
            return circuitState == CircuitState.HALF_OPEN;
        }

        boolean isClosed() {
            return circuitState == CircuitState.CLOSED;
        }
    }

    private static class NoOpCounter implements EventCounter {

        private static final NoOpCounter INSTANCE = new NoOpCounter();

        @Override
        public EventCount getCount() {
            return EventCount.ZERO;
        }

        @Override
        public void onSuccess() {
        }

        @Override
        public void onFailure() {
        }
    }
}
