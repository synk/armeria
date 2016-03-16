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

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * An {@link EventCounter} that accumulates the count of events within a time window.
 */
class SlidingWindowCounter implements EventCounter {

    private final Clock clock;

    private final long windowMillis;

    private final long updateIntervalMillis;

    /**
     * The reference to the latest {@link Bucket}
     */
    private final AtomicReference<Bucket> current;

    /**
     * The reference to the latest accumulated {@link EventCount}
     */
    private final AtomicReference<EventCount> snapshot = new AtomicReference<>(EventCount.ZERO);

    /**
     * The queue that stores {@link Bucket}s within the time window.
     */
    private final Queue<Bucket> reservoir = new ConcurrentLinkedQueue<>();

    SlidingWindowCounter(CircuitBreakerConfig config) {
        requireNonNull(config, "config");
        clock = config.clock();
        windowMillis = config.counterSlidingWindow().toMillis();
        updateIntervalMillis = config.counterUpdateInterval().toMillis();
        current = new AtomicReference<>(new Bucket(clock.currentMillis()));
    }

    @Override
    public EventCount getCount() {
        return snapshot.get();
    }

    @Override
    public void onSuccess() {
        onEvent(Event.SUCCESS);
    }

    @Override
    public void onFailure() {
        onEvent(Event.FAILURE);
    }

    private void onEvent(Event event) {
        final long timeMillis = clock.currentMillis();

        final Bucket currentBucket = current.get();

        if (timeMillis < currentBucket.timestamp()) {
            // if current timestamp is older than bucket's timestamp (maybe race or GC pause?),
            // then creates an instant bucket and puts it to the reservoir not to lose event.
            final Bucket bucket = new Bucket(timeMillis);
            event.increment(bucket);
            reservoir.offer(bucket);
            return;
        } else if (timeMillis < currentBucket.timestamp() + updateIntervalMillis) {
            // increments the current bucket since it is exactly latest
            event.increment(currentBucket);
            return;
        }

        // the current bucket is old
        // it's time to create new one
        final Bucket nextBucket = new Bucket(timeMillis);
        event.increment(nextBucket);

        // replaces the bucket
        if (current.compareAndSet(currentBucket, nextBucket)) {
            // puts old one to the reservoir
            reservoir.offer(currentBucket);
            // and then updates count
            snapshot.set(trimAndSum(timeMillis));
        } else {
            // the bucket has been replaced already
            // puts new one as an instant bucket to the reservoir not to lose event
            reservoir.offer(nextBucket);
        }
    }

    /**
     * Sums up buckets within the time window, and removes all the others.
     */
    private EventCount trimAndSum(long timeMillis) {
        final long oldLimit = timeMillis - windowMillis;
        final Iterator<Bucket> iterator = reservoir.iterator();
        long success = 0, failure = 0;
        while (iterator.hasNext()) {
            final Bucket bucket = iterator.next();
            if (bucket.timestamp < oldLimit) {
                // removes old bucket
                iterator.remove();
            } else {
                success += bucket.success();
                failure += bucket.failure();
            }
        }

        return new EventCount(success, failure);
    }

    private enum Event {
        SUCCESS {
            @Override
            void increment(Bucket bucket) {
                bucket.success.increment();
            }
        },
        FAILURE {
            @Override
            void increment(Bucket bucket) {
                bucket.failure.increment();
            }
        };

        abstract void increment(Bucket bucket);
    }

    /**
     * Holds the count of events within {@code updateIntervalMillis}.
     */
    private static final class Bucket {

        private final long timestamp;

        private final LongAdder success = new LongAdder();

        private final LongAdder failure = new LongAdder();

        private Bucket(long timestamp) {
            this.timestamp = timestamp;
        }

        private long timestamp() {
            return timestamp;
        }

        private long success() {
            return success.sum();
        }

        private long failure() {
            return failure.sum();
        }

        @Override
        public String toString() {
            return "Bucket{" +
                   "timestamp=" + timestamp +
                   ", success=" + success +
                   ", failure=" + failure +
                   '}';
        }
    }
}
