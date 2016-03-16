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

/**
 * Provides a failure detection and fallback mechanism based of
 * <a href="http://martinfowler.com/bliki/CircuitBreaker.html">circuit breaker pattern</a>.
 *
 * <h1>Usage</h1>
 * <h3>Client Example</h3>
 * <pre>{@code
 * CircuitBreakerConfig config = new CircuitBreakerConfigBuilder("hello")
 *                                    .failureRateThreshold(0.5)
 *                                    .build();
 *
 * Iface helloClient = new ClientBuilder("tbinary+http://127.0.0.1:8080/hello")
 *                          .decorator(CircuitBreakerClient.newDecorator(config))
 *                          .build(Iface.class);
 *
 * try {
 *     helloClient.hello("line");
 * } catch (TException e) {
 *     // error handling
 * } catch (FailFastException e) {
 *    // fallback code
 * }
 * }</pre>
 *
 * <h3>Async Client Example</h3>
 * <pre>{@code
 * CircuitBreakerConfig config = new CircuitBreakerConfigBuilder("hello")
 *                                    .failureRateThreshold(0.5)
 *                                    .build();
 *
 * AsyncIface helloClient = new ClientBuilder("tbinary+http://127.0.0.1:8080/hello")
 *                               .decorator(CircuitBreakerClient.newDecorator(config))
 *                               .build(AsyncIface.class);
 *
 * helloClient.hello("line", new AsyncMethodCallback() {
 *     public void onComplete(Object response) {
 *         // response handling
 *     }
 *     public void onError(Exception e) {
 *         if (e instanceof TException) {
 *             // error handling
 *         } else if (e instanceof FailFastException) {
 *             // fallback code
 *         }
 *     }
 * });
 * }</pre>
 *
 * <h1>Circuit States and Transitions</h1>
 * The circuit breaker provided by this package is implemented as a finite state machine consisting of the
 * following states and transitions.
 *
 * <h3>{@code CLOSED}</h3>
 * The initial state. All requests are sent to the remote service. If the failure rate exceeds
 * the specified threshold, the state turns into {@code OPEN}.
 *
 * <h3>{@code OPEN}</h3>
 * All requests fail immediately without calling the remote service. After the specified time,
 * the state turns into {@code HALF_OPEN}.
 *
 * <h3>{@code HALF_OPEN}</h3>
 * Only one trial request is sent at a time.
 * <ul>
 *     <li>If it succeeds, the state turns into {@code CLOSED}.</li>
 *     <li>If it fails, the state returns to {@code OPEN}.</li>
 *     <li>If it doesn't complete within a certain time,
 *     another trial request will be sent again.</li>
 * </ul>
 *
 * <h1>Circuit Breaker Configurations</h1>
 * The behavior of a circuit breaker can be modified via
 * {@link com.linecorp.armeria.client.circuitbreaker.CircuitBreakerConfigBuilder}.
 *
 * <h3>{@code failureRateThreshold}</h3>
 * The threshold of failure rate(= failure/total) to detect a remote service fault.
 *
 * <h3>{@code minimumRequestThreshold}</h3>
 * The minimum number of requests within the time window necessary to detect a remote service
 * fault.
 * <h3>{@code circuitOpenWindow}</h3>
 * The duration of {@code OPEN} state.
 *
 * <h3>{@code trialRequestInterval}</h3>
 * The interval of trial request in {@code HALF_OPEN} state.
 *
 * <h3>{@code counterSlidingWindow}</h3>
 * The time length of sliding window to accumulate the count of events.
 *
 * <h3>{@code counterUpdateInterval}</h3>
 * The interval that a circuit breaker can see the latest count of events.
 *
 * <h3>{@code scope}</h3>
 * The policy of circuit breaker scoping.
 * <ul>
 *     <li>{@code SERVICE} sharing one circuit breaker among all methods of the remote service.</li>
 *     <li>{@code PER_METHOD} binding an individual circuit breaker per method.</li>
 * </ul>
 *
 * <h3>{@code failureFilter}</h3>
 * A filter that decides whether a circuit breaker should deal with a given error.
 *
 */
package com.linecorp.armeria.client.circuitbreaker;