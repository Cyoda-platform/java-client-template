package com.java_template.common.grpc.client.event_handling;

import com.java_template.common.grpc.client.monitoring.EventTracker;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.cyoda.cloud.api.event.processing.CalculationMemberGreetEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GreetEventStrategy implements EventHandlingStrategy<BaseEvent> {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final EventTracker eventTracker;
    private final CloudEventParser cloudEventParser;

    public GreetEventStrategy(
            final EventTracker eventTracker,
            final CloudEventParser cloudEventParser
    ) {
        this.cloudEventParser = cloudEventParser;
        this.eventTracker = eventTracker;
    }

    @Override
    public BaseEvent handleEvent(@NotNull final CloudEvent cloudEvent) {
        eventTracker.trackGreetReceived();
        final CloudEventType cloudEventType = CloudEventType.fromValue(cloudEvent.getType());

        log.debug(
                "[IN] Received event {}: \n{}",
                cloudEventType,
                cloudEvent.getTextData()
        );

        cloudEventParser.parseCloudEvent(cloudEvent, CalculationMemberGreetEvent.class)
                .ifPresent(event -> log.info("Received greet event: {}", event));

        return null;
    }

    @Override
    public boolean supports(@NotNull final CloudEventType eventType) {
        return CloudEventType.CALCULATION_MEMBER_GREET_EVENT.equals(eventType);
    }
}
