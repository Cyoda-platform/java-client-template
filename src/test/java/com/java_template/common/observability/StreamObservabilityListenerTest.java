package com.java_template.common.observability;

import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ABOUTME: Tests for StreamObservabilityListener — verifies that gRPC bidirectional
 * stream lifecycle events are correctly tracked as OpenTelemetry gauge metrics for
 * active streams, keep-alive age, and pending ACK counts.
 */
class StreamObservabilityListenerTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private long readGauge(String metricName) {
        List<MetricData> metrics = otelTesting.getMetrics();
        return metrics.stream()
                .filter(m -> m.getName().equals(metricName))
                .findFirst()
                .map(m -> {
                    Collection<LongPointData> points = m.getLongGaugeData().getPoints();
                    return points.stream()
                            .mapToLong(LongPointData::getValue)
                            .findFirst()
                            .orElse(0L);
                })
                .orElse(0L);
    }

    @Test
    void shouldIncrementActiveStreamsOnOpen() {
        var listener = new StreamObservabilityListener(otelTesting.getOpenTelemetry());

        listener.onStreamOpen();

        assertEquals(1L, readGauge("cyoda.grpc.stream.active"));
    }

    @Test
    void shouldDecrementActiveStreamsOnClose() {
        var listener = new StreamObservabilityListener(otelTesting.getOpenTelemetry());

        listener.onStreamOpen();
        listener.onStreamClose();

        assertEquals(0L, readGauge("cyoda.grpc.stream.active"));
    }

    @Test
    void shouldDecrementActiveStreamsOnError() {
        var listener = new StreamObservabilityListener(otelTesting.getOpenTelemetry());

        listener.onStreamOpen();
        listener.onStreamError();

        assertEquals(0L, readGauge("cyoda.grpc.stream.active"));
    }

    @Test
    void shouldTrackKeepAliveAge() {
        var listener = new StreamObservabilityListener(otelTesting.getOpenTelemetry());

        listener.onKeepAlive(System.currentTimeMillis());

        long ageSeconds = readGauge("cyoda.grpc.keepalive.age");
        assertTrue(ageSeconds >= 0 && ageSeconds <= 2,
                "Keep-alive age should be near 0 seconds, but was: " + ageSeconds);
    }

    @Test
    void shouldTrackPendingAcks() {
        var listener = new StreamObservabilityListener(otelTesting.getOpenTelemetry());

        listener.onEventSent();
        listener.onEventSent();
        listener.onAckReceived();

        assertEquals(1L, readGauge("cyoda.grpc.ack.pending"));
    }
}
