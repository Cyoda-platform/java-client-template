package com.java_template.common.observability;

import com.java_template.common.grpc.client.event_handling.EventHandler;
import io.cloudevents.v1.proto.CloudEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;

import java.util.Set;

/**
 * ABOUTME: Decorator that wraps an EventHandler with OpenTelemetry spans and metrics.
 * Creates a span per CloudEvent processed, records event type and ID as attributes,
 * and tracks duration histograms and event/error counters.
 */
public class ObservableEventHandler implements EventHandler {

    private static final AttributeKey<String> EVENT_TYPE_KEY = AttributeKey.stringKey("cyoda.event.type");
    private static final AttributeKey<String> EVENT_ID_KEY = AttributeKey.stringKey("cyoda.event.id");
    private static final AttributeKey<String> OUTCOME_KEY = AttributeKey.stringKey("outcome");
    private static final AttributeKey<String> EXCEPTION_CLASS_KEY = AttributeKey.stringKey("exception_class");

    private final EventHandler delegate;
    private final Tracer tracer;
    private final DoubleHistogram eventDuration;
    private final LongCounter eventCount;
    private final LongCounter eventErrors;

    public ObservableEventHandler(EventHandler delegate, OpenTelemetry openTelemetry) {
        this.delegate = delegate;
        this.tracer = openTelemetry.getTracer("com.java_template.common.observability");

        Meter meter = openTelemetry.getMeter("com.java_template.common.observability");
        this.eventDuration = meter.histogramBuilder("cyoda.event.duration")
                .setDescription("CloudEvent processing duration in seconds")
                .setUnit("s")
                .build();
        this.eventCount = meter.counterBuilder("cyoda.event.count")
                .setDescription("Total CloudEvents processed")
                .build();
        this.eventErrors = meter.counterBuilder("cyoda.event.errors")
                .setDescription("Total CloudEvent processing failures")
                .build();
    }

    @Override
    public void handleEvent(CloudEvent cloudEvent) {
        String eventType = cloudEvent.getType();
        Span span = tracer.spanBuilder("cyoda.event " + eventType)
                .setAttribute(EVENT_TYPE_KEY, eventType)
                .setAttribute(EVENT_ID_KEY, cloudEvent.getId())
                .startSpan();

        long startNanos = System.nanoTime();
        try (var scope = span.makeCurrent()) {
            delegate.handleEvent(cloudEvent);
            eventCount.add(1, Attributes.of(EVENT_TYPE_KEY, eventType, OUTCOME_KEY, "success"));
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            eventCount.add(1, Attributes.of(EVENT_TYPE_KEY, eventType, OUTCOME_KEY, "error"));
            eventErrors.add(1, Attributes.of(EVENT_TYPE_KEY, eventType, EXCEPTION_CLASS_KEY, e.getClass().getName()));
            throw e;
        } finally {
            double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
            eventDuration.record(durationSeconds, Attributes.of(EVENT_TYPE_KEY, eventType));
            span.end();
        }
    }

    @Override
    public Set<String> getSupportedTags() {
        return delegate.getSupportedTags();
    }
}
