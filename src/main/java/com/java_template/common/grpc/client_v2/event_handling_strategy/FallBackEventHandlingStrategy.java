package com.java_template.common.grpc.client_v2.event_handling_strategy;

import com.java_template.common.grpc.client.EventHandlingStrategy;
import io.cloudevents.v1.proto.CloudEvent;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

//@Component
@Order // NOTE: `LOWEST_PRECEDENCE` by default
public class FallBackEventHandlingStrategy implements EventHandlingStrategy<BaseEvent> {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Override
    public BaseEvent handleEvent(@NotNull final CloudEvent cloudEvent) {
        CloudEventType cloudEventType = CloudEventType.fromValue(cloudEvent.getType());

        log.warn(
                "[IN] Received UNHANDLED event type {}: \n{}",
                cloudEventType,
                cloudEvent.getTextData()
        );

        return null;
    }

    @Override
    public boolean supports(@NotNull final CloudEventType eventType) {
        return true;
    }
}
