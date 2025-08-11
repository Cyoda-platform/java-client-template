package com.java_template.common.grpc.client_v2;

import com.google.protobuf.InvalidProtocolBufferException;
import com.java_template.common.grpc.client.EventHandlingStrategy;
import io.cloudevents.v1.proto.CloudEvent;
import java.util.List;
import java.util.Set;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
class CyodaCalculationMemberClient implements EventHandler {

    private static final Set<String> TAGS = Set.of(""); // TODO: Move to props

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final EventSender eventSender;
    private final CalculationExecutionStrategy calculationExecutionStrategy;
    private final CloudEventBuilder eventBuilder;
    private final List<EventHandlingStrategy<? extends BaseEvent>> eventHandlingStrategies;

    CyodaCalculationMemberClient(
            @Lazy final EventSender eventSender,
            final CalculationExecutionStrategy calculationExecutionStrategy,
            final CloudEventBuilder eventBuilder,
            final List<EventHandlingStrategy<? extends BaseEvent>> eventHandlingStrategies
    ) {
        this.eventSender = eventSender;
        this.calculationExecutionStrategy = calculationExecutionStrategy;
        this.eventBuilder = eventBuilder;
        this.eventHandlingStrategies = eventHandlingStrategies;
    }

    @Override
    public void handleEvent(final CloudEvent cloudEvent) {
        calculationExecutionStrategy.run(() -> {
            try {
                final var cloudEventType = CloudEventType.fromValue(cloudEvent.getType());
                log.debug(
                        "[IN] Received event {}: \n{}",
                        cloudEventType,
                        cloudEvent.getTextData()
                );

                final var strategy = eventHandlingStrategies.stream()
                        .filter(it -> it.supports(cloudEventType))
                        .findFirst()
                        .orElse(null);

                if (strategy == null) {
                    log.error("No handler strategy found for event {}", cloudEventType);
                    return;
                }

                log.debug(
                        "Using strategy '{}' for event type '{}'",
                        strategy.getClass().getSimpleName(),
                        cloudEventType
                );

                // The contract on handleEvent is that it does not throw Exceptions,
                // but handles them internally and returns an error response.
                final var response = strategy.handleEvent(cloudEvent);
                if (response != null) {
                    sendEvent(response);
                } else {
                    log.debug(
                            "Nothing to respond for event '{}':'{}'",
                            cloudEventType,
                            cloudEvent.getId()
                    );
                }

            } catch (Exception e) {
                log.error("Error processing event: {}", cloudEvent, e);
            }
        });
    }

    @Override
    public Set<String> getSupportedTags() {
        return TAGS;
    }

    private void sendEvent(final BaseEvent event) {
        final CloudEvent cloudEvent;
        try {
            cloudEvent = eventBuilder.buildEvent(event);
        } catch (InvalidProtocolBufferException e) {
            // TODO: Define the strategy for handling serialization errors.
            //  For now we just log it.
            log.error("Failed to parse cloud event", e);
            return;
        }

        if (event.getSuccess()) {
            log.info("[OUT] Sending event {}, success: {}", cloudEvent.getType(), event.getSuccess());
        } else {
            log.warn("[OUT] Sending event {}, success: {}", cloudEvent.getType(), event.getSuccess());
        }

        eventSender.sendEvent(cloudEvent);
    }
}
