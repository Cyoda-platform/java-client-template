package com.java_template.common.grpc.client_v2.event_handling_strategy;

import com.java_template.common.grpc.client.EventHandlingStrategy;
import com.java_template.common.grpc.client_v2.CloudEventParser;
import com.java_template.common.grpc.client_v2.EventTracker;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.cyoda.cloud.api.event.processing.EventAckResponse;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

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
