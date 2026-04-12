package com.java_template.common.observability;

import com.java_template.common.grpc.client.event_handling.EventHandler;
import io.cloudevents.v1.proto.CloudEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.opentelemetry.sdk.metrics.data.MetricData;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Tests for ObservableEventHandler — a decorator that wraps EventHandler
 * with OpenTelemetry spans and metrics for CloudEvent processing observability.
 */
class ObservableEventHandlerTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    private static final AttributeKey<String> EVENT_TYPE_KEY = AttributeKey.stringKey("cyoda.event.type");
    private static final AttributeKey<String> EVENT_ID_KEY = AttributeKey.stringKey("cyoda.event.id");

    private CloudEvent buildTestEvent(String type, String id) {
        return CloudEvent.newBuilder()
                .setType(type)
                .setId(id)
                .setSource("test-source")
                .build();
    }

    @Test
    void shouldCreateSpanWithCorrectNameAndAttributes() {
        var delegate = new RecordingEventHandler();
        var handler = new ObservableEventHandler(delegate, otelTesting.getOpenTelemetry());
        var event = buildTestEvent("ENTITY_PROCESSOR_CALCULATION_REQUEST", "evt-001");

        handler.handleEvent(event);

        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(1, spans.size());

        SpanData span = spans.get(0);
        assertEquals("cyoda.event ENTITY_PROCESSOR_CALCULATION_REQUEST", span.getName());
        assertEquals("ENTITY_PROCESSOR_CALCULATION_REQUEST", span.getAttributes().get(EVENT_TYPE_KEY));
        assertEquals("evt-001", span.getAttributes().get(EVENT_ID_KEY));
    }

    @Test
    void shouldDelegateToWrappedHandler() {
        var delegate = new RecordingEventHandler();
        var handler = new ObservableEventHandler(delegate, otelTesting.getOpenTelemetry());
        var event = buildTestEvent("ENTITY_PROCESSOR_CALCULATION_REQUEST", "evt-002");

        handler.handleEvent(event);

        assertEquals(1, delegate.eventsReceived.size());
        assertEquals("evt-002", delegate.eventsReceived.get(0).getId());
    }

    @Test
    void shouldMarkSpanAsErrorWhenDelegateThrows() {
        var delegate = new ThrowingEventHandler(new RuntimeException("processor failed"));
        var handler = new ObservableEventHandler(delegate, otelTesting.getOpenTelemetry());
        var event = buildTestEvent("ENTITY_PROCESSOR_CALCULATION_REQUEST", "evt-003");

        assertThrows(RuntimeException.class, () -> handler.handleEvent(event));

        List<SpanData> spans = otelTesting.getSpans();
        assertEquals(1, spans.size());
        assertEquals(StatusCode.ERROR, spans.get(0).getStatus().getStatusCode());
        assertFalse(spans.get(0).getEvents().isEmpty(), "Should have exception event recorded");
    }

    @Test
    void shouldDelegateSupportedTags() {
        var delegate = new RecordingEventHandler();
        var handler = new ObservableEventHandler(delegate, otelTesting.getOpenTelemetry());

        assertEquals(Set.of("test-tag"), handler.getSupportedTags());
    }

    @Test
    void shouldRecordDurationMetricOnSuccess() {
        var delegate = new RecordingEventHandler();
        var handler = new ObservableEventHandler(delegate, otelTesting.getOpenTelemetry());
        var event = buildTestEvent("ENTITY_PROCESSOR_CALCULATION_REQUEST", "evt-005");

        handler.handleEvent(event);

        assertTrue(hasMetric("cyoda.event.duration"), "Should record duration histogram");
    }

    @Test
    void shouldRecordEventCountOnSuccess() {
        var delegate = new RecordingEventHandler();
        var handler = new ObservableEventHandler(delegate, otelTesting.getOpenTelemetry());
        var event = buildTestEvent("ENTITY_PROCESSOR_CALCULATION_REQUEST", "evt-006");

        handler.handleEvent(event);

        assertTrue(hasMetric("cyoda.event.count"), "Should record event count");
    }

    @Test
    void shouldRecordErrorCounterWhenDelegateThrows() {
        var delegate = new ThrowingEventHandler(new RuntimeException("fail"));
        var handler = new ObservableEventHandler(delegate, otelTesting.getOpenTelemetry());
        var event = buildTestEvent("ENTITY_PROCESSOR_CALCULATION_REQUEST", "evt-007");

        assertThrows(RuntimeException.class, () -> handler.handleEvent(event));

        assertTrue(hasMetric("cyoda.event.errors"), "Should record error counter");
    }

    private boolean hasMetric(String name) {
        Collection<MetricData> metrics = otelTesting.getMetrics();
        return metrics.stream().anyMatch(m -> m.getName().equals(name));
    }

    // --- Test helpers ---

    private static class RecordingEventHandler implements EventHandler {
        final java.util.ArrayList<CloudEvent> eventsReceived = new java.util.ArrayList<>();

        @Override
        public void handleEvent(CloudEvent cloudEvent) {
            eventsReceived.add(cloudEvent);
        }

        @Override
        public Set<String> getSupportedTags() {
            return Set.of("test-tag");
        }
    }

    private static class ThrowingEventHandler implements EventHandler {
        private final RuntimeException exception;

        ThrowingEventHandler(RuntimeException exception) {
            this.exception = exception;
        }

        @Override
        public void handleEvent(CloudEvent cloudEvent) {
            throw exception;
        }

        @Override
        public Set<String> getSupportedTags() {
            return Set.of();
        }
    }
}
