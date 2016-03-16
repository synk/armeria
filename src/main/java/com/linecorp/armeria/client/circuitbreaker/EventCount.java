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

/**
 * An immutable object that stores the count of events.
 */
class EventCount {

    static final EventCount ZERO = new EventCount(0, 0);

    private final long success;

    private final long failure;

    EventCount(long success, long failure) {
        this.success = success;
        this.failure = failure;
        assert 0 <= success;
        assert 0 <= failure;
    }

    long success() {
        return success;
    }

    long failure() {
        return failure;
    }

    long total() {
        return success + failure;
    }

    double failureRate() {
        return failure / (double) (success + failure);
    }

    @Override
    public String toString() {
        return "EventCount{" +
               "success=" + success +
               ", failure=" + failure +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventCount that = (EventCount) o;
        return success == that.success && failure == that.failure;
    }

}
