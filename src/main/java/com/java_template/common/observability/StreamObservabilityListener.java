package com.java_template.common.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * ABOUTME: Tracks gRPC bidirectional stream lifecycle metrics using OpenTelemetry gauges.
 * Records active stream count, seconds since last keep-alive heartbeat, and the number
 * of sent events awaiting acknowledgement.
 */
public class StreamObservabilityListener {

    private final AtomicLong activeStreams = new AtomicLong(0);
    private final AtomicLong lastKeepAliveMs = new AtomicLong(0);
    private final AtomicLong pendingAcks = new AtomicLong(0);

    public StreamObservabilityListener(OpenTelemetry openTelemetry) {
        Meter meter = openTelemetry.getMeter("com.java_template.common.observability");

        meter.gaugeBuilder("cyoda.grpc.stream.active")
                .ofLongs()
                .setDescription("Number of active gRPC bidirectional streams")
                .buildWithCallback(measurement -> measurement.record(activeStreams.get()));

        meter.gaugeBuilder("cyoda.grpc.keepalive.age")
                .ofLongs()
                .setDescription("Seconds since the last gRPC keep-alive heartbeat was received")
                .buildWithCallback(measurement -> {
                    long last = lastKeepAliveMs.get();
                    if (last == 0) {
                        measurement.record(0);
                    } else {
                        measurement.record((System.currentTimeMillis() - last) / 1000);
                    }
                });

        meter.gaugeBuilder("cyoda.grpc.ack.pending")
                .ofLongs()
                .setDescription("Number of sent events awaiting acknowledgement")
                .buildWithCallback(measurement -> measurement.record(pendingAcks.get()));
    }

    /** Called when a new gRPC bidirectional stream is opened. */
    public void onStreamOpen() {
        activeStreams.incrementAndGet();
    }

    /** Called when a gRPC stream closes normally. */
    public void onStreamClose() {
        activeStreams.decrementAndGet();
    }

    /** Called when a gRPC stream closes due to an error. */
    public void onStreamError() {
        activeStreams.decrementAndGet();
    }

    /**
     * Called when a keep-alive heartbeat is received.
     *
     * @param timestampMs the wall-clock time of the heartbeat in epoch milliseconds
     */
    public void onKeepAlive(long timestampMs) {
        lastKeepAliveMs.set(timestampMs);
    }

    /** Called when an event is sent over the stream, incrementing the pending ACK count. */
    public void onEventSent() {
        pendingAcks.incrementAndGet();
    }

    /** Called when an ACK is received for a previously sent event, decrementing the pending count. */
    public void onAckReceived() {
        pendingAcks.decrementAndGet();
    }
}
