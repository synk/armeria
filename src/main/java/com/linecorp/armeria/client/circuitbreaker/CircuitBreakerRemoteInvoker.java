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

import java.lang.reflect.Method;
import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.linecorp.armeria.client.ClientCodec;
import com.linecorp.armeria.client.ClientOptions;
import com.linecorp.armeria.client.DecoratingRemoteInvoker;
import com.linecorp.armeria.client.RemoteInvoker;
import com.linecorp.armeria.client.circuitbreaker.CircuitBreakerConfig.Scope;

import io.netty.channel.EventLoop;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

/**
 * A {@link DecoratingRemoteInvoker} that deals with failures of remote invocation based on circuit breaker
 * pattern.
 */
class CircuitBreakerRemoteInvoker extends DecoratingRemoteInvoker {

    private final CircuitBreakerContainer container;

    private final CircuitBreakerConfig config;

    /**
     * Creates a new instance that decorates the given {@link RemoteInvoker}.
     */
    CircuitBreakerRemoteInvoker(RemoteInvoker delegate, CircuitBreakerConfig config) {
        super(delegate);
        this.config = requireNonNull(config, "config");

        // selects an implementation of container according to Scope
        if (config.scope() == Scope.PER_METHOD) {
            container = newPerMethodContainer(config);
        } else {
            container = newServiceWideContainer(config);
        }
    }

    @Override
    public <T> Future<T> invoke(EventLoop eventLoop, URI uri, ClientOptions options, ClientCodec codec,
                                Method method, Object[] args) throws Exception {

        final CircuitBreaker circuitBreaker = container.get(uri, options, codec, method, args);

        if (circuitBreaker.canRequest()) {
            final Future<T> resultFut = delegate().invoke(eventLoop, uri, options, codec, method, args);
            resultFut.addListener(future -> {
                if (future.isSuccess()) {
                    // reports success event
                    circuitBreaker.onSuccess();
                } else {
                    final Throwable cause = future.cause();
                    if (cause == null || config.failureFilter().shouldDealWith(cause)) {
                        // reports failure event
                        circuitBreaker.onFailure();
                    }
                }
            });
            return resultFut;
        } else {
            // the circuit is tripped

            // prepares a failed resultPromise
            final Promise<T> resultPromise = eventLoop.newPromise();
            resultPromise.setFailure(new FailFastException(config.remoteServiceName(), method.getName()));
            codec.prepareRequest(method, args, resultPromise);

            // returns immediately without calling succeeding remote invokers
            return resultPromise;
        }
    }

    /**
     * A container that implements a strategy of circuit breaker scoping.
     */
    @FunctionalInterface
    private interface CircuitBreakerContainer {
        CircuitBreaker get(URI uri, ClientOptions options, ClientCodec codec, Method method, Object[] args);
    }

    /**
     * A {@link CircuitBreakerContainer} that shares one circuit breaker among all methods.
     */
    private static CircuitBreakerContainer newServiceWideContainer(CircuitBreakerConfig config) {
        final CircuitBreaker circuitBreaker = new CircuitBreaker(config.remoteServiceName(), config);
        return (uri, options, codec, method, args) -> circuitBreaker;
    }

    /**
     * A {@link CircuitBreakerContainer} that assigns one circuit breaker per method.
     */
    private static CircuitBreakerContainer newPerMethodContainer(CircuitBreakerConfig config) {
        final ConcurrentMap<String, CircuitBreaker> mapping = new ConcurrentHashMap<>();

        return (uri, options, codec, method, args) -> {
            final CircuitBreaker circuitBreaker = mapping.get(method.getName());
            if (circuitBreaker != null) {
                return circuitBreaker;
            }
            final String name = config.remoteServiceName() + '#' + method.getName();
            return mapping.computeIfAbsent(method.getName(), (key) -> new CircuitBreaker(name, config));
        };
    }

}
