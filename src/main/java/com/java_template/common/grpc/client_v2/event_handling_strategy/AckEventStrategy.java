package com.java_template.common.grpc.client_v2.event_handling_strategy;

import com.java_template.common.grpc.client.EventHandlingStrategy;
import com.java_template.common.grpc.client_v2.CloudEventParser;
import com.java_template.common.grpc.client_v2.EventTracker;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.cyoda.cloud.api.event.processing.EventAckResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AckEventStrategy implements EventHandlingStrategy<BaseEvent> {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final EventTracker eventTracker;
    private final CloudEventParser cloudEventParser;

    public AckEventStrategy(
            final EventTracker eventTracker,
            final CloudEventParser cloudEventParser
    ) {
        this.eventTracker = eventTracker;
        this.cloudEventParser = cloudEventParser;
    }

    @Override
    public BaseEvent handleEvent(@NotNull final CloudEvent cloudEvent) {
        final var cloudEventType = CloudEventType.fromValue(cloudEvent.getType());

        log.debug(
                "[IN] Received event {}: \n{}",
                cloudEventType,
                cloudEvent.getTextData()
        );

        cloudEventParser.parseCloudEvent(cloudEvent, EventAckResponse.class)
                .ifPresent(eventTracker::trackAcknowledge);

        return null;
    }

    @Override
    public boolean supports(@NotNull final CloudEventType eventType) {
        return CloudEventType.EVENT_ACK_RESPONSE.equals(eventType);
    }

}
