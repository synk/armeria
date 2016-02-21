/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.server.tracing.http;

import javax.annotation.Nullable;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.IdConversion;
import com.github.kristofa.brave.SpanId;
import com.github.kristofa.brave.TraceData;
import com.github.kristofa.brave.http.BraveHttpHeaders;

import com.linecorp.armeria.common.ServiceInvocationContext;
import com.linecorp.armeria.server.ServiceInvocationHandler;
import com.linecorp.armeria.server.tracing.TracingServiceInvocationHandler;

import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

/**
 * A {@link TracingServiceInvocationHandler} that uses HTTP headers as a container of trace data.
 */
class HttpTracingServiceInvocationHandler extends TracingServiceInvocationHandler {

    HttpTracingServiceInvocationHandler(ServiceInvocationHandler handler, Brave brave) {
        super(handler, brave);
    }

    @Override
    @Nullable
    protected TraceData getTraceData(ServiceInvocationContext ctx) {
        final Object request = ctx.originalRequest();
        if (request == null || !(request instanceof HttpRequest)) {
            return null; // The request is not http
        }

        final HttpHeaders headers = ((HttpRequest) request).headers();

        // The following HTTP trace header spec is based on
        // com.github.kristofa.brave.http.HttpServerRequestAdapter#getTraceData

        final String sampled = headers.get(BraveHttpHeaders.Sampled.getName());
        if ("1".equals(sampled)) {
            final String traceId = headers.get(BraveHttpHeaders.TraceId.getName());
            final String spanId = headers.get(BraveHttpHeaders.SpanId.getName());
            if (traceId == null || spanId == null) {
                return null;
            }
            final String parentSpanId = headers.get(BraveHttpHeaders.ParentSpanId.getName());
            final SpanId span = getSpanId(traceId, spanId, parentSpanId);
            return TraceData.builder().sample(true).spanId(span).build();
        } else {
            return TraceData.builder().sample(false).build();
        }
    }

    private SpanId getSpanId(String traceId, String spanId, String parentSpanId) {
        if (parentSpanId != null) {
            return SpanId.create(IdConversion.convertToLong(traceId),
                                 IdConversion.convertToLong(spanId),
                                 IdConversion.convertToLong(parentSpanId));
        }
        return SpanId.create(IdConversion.convertToLong(traceId), IdConversion.convertToLong(spanId), null);
    }

}
