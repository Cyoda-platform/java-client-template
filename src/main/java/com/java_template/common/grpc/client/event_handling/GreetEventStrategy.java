package com.java_template.common.grpc.client.event_handling;

import com.java_template.common.grpc.client.connection.GreetEventListener;
import com.java_template.common.grpc.client.monitoring.EventTracker;
import io.cloudevents.v1.proto.CloudEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.cyoda.cloud.api.event.processing.CalculationMemberGreetEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


/**
 * ABOUTME: Event handling strategy for processing greet events during connection
 * establishment with promise-based completion tracking.
 */
@Component
public class GreetEventStrategy implements EventHandlingStrategy<BaseEvent>, GreetEventListener {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final AtomicReference<GreetPromiseContext> promiseContextRef = new AtomicReference<>();
    private final EventTracker eventTracker;

    private final CloudEventParser cloudEventParser;
    public GreetEventStrategy(final EventTracker eventTracker, final CloudEventParser cloudEventParser) {
        this.cloudEventParser = cloudEventParser;
        this.eventTracker = eventTracker;
    }

    @Override
    public void registerPendingGreetEvent(final String joinEventId, final CompletableFuture<String> greetPromise) {
        promiseContextRef.set(new GreetPromiseContext(joinEventId, greetPromise));
    }

    @Override
    public BaseEvent handleEvent(@NotNull final CloudEvent cloudEvent) {
        final var context = promiseContextRef.get();
        if (context != null && !context.promise().isDone()) {
            context.promise().complete(context.joinEventId());
        }

        eventTracker.trackGreetReceived();
        final CloudEventType cloudEventType = CloudEventType.fromValue(cloudEvent.getType());

        log.debug("[IN] Received event {}: \n{}", cloudEventType, cloudEvent.getTextData());

        cloudEventParser.parseCloudEvent(cloudEvent, CalculationMemberGreetEvent.class)
                .ifPresent(event -> log.info("Received greet event: {}", event));

        return null;
    }

    @Override
    public boolean supports(@NotNull final CloudEventType eventType) {
        return CloudEventType.CALCULATION_MEMBER_GREET_EVENT.equals(eventType);
    }

    private record GreetPromiseContext(
            String joinEventId, CompletableFuture<String> promise
    ) {}
}
