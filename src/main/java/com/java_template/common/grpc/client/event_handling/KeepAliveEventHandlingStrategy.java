package com.java_template.common.grpc.client.event_handling;

import com.java_template.common.grpc.client.monitoring.EventTracker;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.cyoda.cloud.api.event.processing.EventAckResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * ABOUTME: Event handling strategy for processing keep-alive events
 * to maintain gRPC connection health and monitoring.
 */
@Component
public class KeepAliveEventHandlingStrategy implements EventHandlingStrategy<EventAckResponse> {

    private final CloudEventParser cloudEventParser;
    private final EventTracker eventTracker;

    public KeepAliveEventHandlingStrategy(
            final CloudEventParser cloudEventParser,
            final EventTracker eventTracker
    ) {
        this.cloudEventParser = cloudEventParser;
        this.eventTracker = eventTracker;
    }

    @Override
    public boolean supports(@NotNull final CloudEventType eventType) {
        return CloudEventType.CALCULATION_MEMBER_KEEP_ALIVE_EVENT.equals(eventType);
    }

    @Override
    public EventAckResponse handleEvent(@NotNull final CloudEvent cloudEvent) {
        eventTracker.trackKeepAlive(System.currentTimeMillis());
        final var resp = cloudEventParser.parseCloudEvent(
                cloudEvent,
                EventAckResponse.class
        ).orElse(null);

        if (resp == null) {
            return null;
        }

        resp.setSourceEventId(resp.getId());
        return resp;
    }
}
