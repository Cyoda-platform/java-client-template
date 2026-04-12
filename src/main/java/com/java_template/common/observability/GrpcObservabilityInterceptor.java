package com.java_template.common.observability;

import io.grpc.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

/**
 * ABOUTME: gRPC ClientInterceptor that creates OpenTelemetry spans for outgoing Cyoda gRPC calls.
 * Records the full method name as the span name and rpc.method attribute, tracks gRPC status
 * codes as a counter, and marks spans as ERROR when a call closes with a non-OK status.
 */
public class GrpcObservabilityInterceptor implements ClientInterceptor {

    private static final AttributeKey<String> RPC_METHOD_KEY = AttributeKey.stringKey("rpc.method");
    private static final AttributeKey<Long> GRPC_STATUS_CODE_KEY = AttributeKey.longKey("rpc.grpc.status_code");
    private static final AttributeKey<String> STATUS_CODE_NAME_KEY = AttributeKey.stringKey("status_code");
    private static final AttributeKey<String> METHOD_KEY = AttributeKey.stringKey("method");

    private final Tracer tracer;
    private final LongCounter grpcStatusCounter;

    public GrpcObservabilityInterceptor(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("com.java_template.common.observability");
        Meter meter = openTelemetry.getMeter("com.java_template.common.observability");
        this.grpcStatusCounter = meter.counterBuilder("cyoda.grpc.status")
                .setDescription("gRPC call outcomes by method and status code")
                .build();
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {

        String fullMethodName = method.getFullMethodName();
        Span span = tracer.spanBuilder("gRPC " + fullMethodName)
                .setAttribute(RPC_METHOD_KEY, fullMethodName)
                .startSpan();

        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        span.setAttribute(GRPC_STATUS_CODE_KEY, (long) status.getCode().value());
                        grpcStatusCounter.add(1, Attributes.of(
                                METHOD_KEY, fullMethodName,
                                STATUS_CODE_NAME_KEY, status.getCode().name()
                        ));
                        if (!status.isOk()) {
                            span.setStatus(StatusCode.ERROR, status.getDescription());
                        }
                        span.end();
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
