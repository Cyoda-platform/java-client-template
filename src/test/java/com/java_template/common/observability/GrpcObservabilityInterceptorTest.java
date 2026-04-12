package com.java_template.common.observability;

import io.grpc.*;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Tests for GrpcObservabilityInterceptor — verifies that gRPC calls produce
 * OpenTelemetry spans with method attributes and correct status tracking on close.
 */
class GrpcObservabilityInterceptorTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private static final AttributeKey<String> RPC_METHOD_KEY = AttributeKey.stringKey("rpc.method");
    private static final AttributeKey<Long> GRPC_STATUS_KEY = AttributeKey.longKey("rpc.grpc.status_code");

    /** Minimal marshaller for using String as the gRPC request/response type in tests. */
    private static class StringMarshaller implements MethodDescriptor.Marshaller<String> {
        @Override
        public java.io.InputStream stream(String value) {
            return new java.io.ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String parse(java.io.InputStream stream) {
            try {
                return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final MethodDescriptor<String, String> TEST_METHOD = MethodDescriptor.<String, String>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("CloudEventsService/entityManage")
            .setRequestMarshaller(new StringMarshaller())
            .setResponseMarshaller(new StringMarshaller())
            .build();

    /**
     * Channel that captures the listener passed to start() so tests can drive
     * the call lifecycle by invoking onClose() directly.
     */
    private static class CapturingChannel extends Channel {
        final AtomicReference<ClientCall.Listener<?>> capturedListener = new AtomicReference<>();

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            return new ClientCall<>() {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    capturedListener.set(responseListener);
                }
                @Override public void request(int numMessages) {}
                @Override public void cancel(String message, Throwable cause) {}
                @Override public void halfClose() {}
                @Override public void sendMessage(ReqT message) {}
            };
        }

        @Override
        public String authority() {
            return "test-authority";
        }
    }

    @Test
    void shouldAddCustomAttributesToSpan() {
        var interceptor = new GrpcObservabilityInterceptor(otelTesting.getOpenTelemetry());
        var channel = new CapturingChannel();
        var call = interceptor.interceptCall(TEST_METHOD, CallOptions.DEFAULT, channel);

        call.start(new ClientCall.Listener<>() {}, new Metadata());

        // Close with OK so the span ends and becomes visible in the test exporter
        @SuppressWarnings("unchecked")
        ClientCall.Listener<String> listener = (ClientCall.Listener<String>) channel.capturedListener.get();
        assertNotNull(listener, "Listener should have been captured by CapturingChannel");
        listener.onClose(Status.OK, new Metadata());

        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals("gRPC CloudEventsService/entityManage", span.getName());
        assertEquals("CloudEventsService/entityManage", span.getAttributes().get(RPC_METHOD_KEY));
    }

    @Test
    void shouldRecordGrpcStatusOnClose() {
        var interceptor = new GrpcObservabilityInterceptor(otelTesting.getOpenTelemetry());
        var channel = new CapturingChannel();
        var call = interceptor.interceptCall(TEST_METHOD, CallOptions.DEFAULT, channel);

        call.start(new ClientCall.Listener<>() {}, new Metadata());

        // Simulate the server closing the call with DEADLINE_EXCEEDED
        @SuppressWarnings("unchecked")
        ClientCall.Listener<String> listener = (ClientCall.Listener<String>) channel.capturedListener.get();
        assertNotNull(listener, "Listener should have been captured by CapturingChannel");
        listener.onClose(Status.DEADLINE_EXCEEDED, new Metadata());

        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals(StatusCode.ERROR, span.getStatus().getStatusCode());
        Long statusCode = span.getAttributes().get(GRPC_STATUS_KEY);
        assertNotNull(statusCode);
        assertEquals(Status.DEADLINE_EXCEEDED.getCode().value(), statusCode.intValue());
    }
}
