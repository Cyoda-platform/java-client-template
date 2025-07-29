package com.java_template.common.grpc.client_v2;

import com.google.protobuf.InvalidProtocolBufferException;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.UUID;
import javax.annotation.PostConstruct;
import org.cyoda.cloud.api.event.processing.CalculationMemberJoinEvent;
import org.cyoda.cloud.api.grpc.CloudEventsServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class ConnectionManager implements EventSender {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final EventHandler eventHandler;
    private final EventTracker eventTracker;
    private final ObserverStateTracker observerStateTracker;
    private final EventBuilder eventBuilder;
    private final CloudEventsServiceGrpc.CloudEventsServiceStub cloudEventsServiceStub;
    private final ReconnectionStrategy reconnectionStrategy;

    private StreamObserver<CloudEvent> streamObserver;

    public ConnectionManager(
            final EventHandler eventHandler,
            final EventTracker eventTracker,
            final ObserverStateTracker observerStateTracker,
            final EventBuilder eventBuilder,
            final CloudEventsServiceGrpc.CloudEventsServiceStub cloudEventsServiceStub,
            final ReconnectionStrategy reconnectionStrategy
    ) {
        this.eventHandler = eventHandler;
        this.eventTracker = eventTracker;
        this.observerStateTracker = observerStateTracker;
        this.eventBuilder = eventBuilder;
        this.cloudEventsServiceStub = cloudEventsServiceStub;
        this.reconnectionStrategy = reconnectionStrategy;
    }

    private CloudEvent createJoinEvent(
            final String id,
            final Set<String> tags
    ) throws InvalidProtocolBufferException {
        final var event = new CalculationMemberJoinEvent();
        event.setId(id);
        event.setTags(tags.stream().toList());

        log.info("Member status updated to JOINING with join event ID: {}", id);

        return eventBuilder.buildEvent(event);
    }

    @PostConstruct
    private void init() {
        if (streamObserver != null) {
            streamObserver.onCompleted();
        }
        try {
            streamObserver = connect();
        } catch (Exception e) {
            requestReconnection();
        }
    }

    private StreamObserver<CloudEvent> connect() throws InvalidProtocolBufferException {
        observerStateTracker.trackObserverStateChange(ObserverState.CONNECTING);
        final var newObserver = cloudEventsServiceStub.startStreaming(
                new CloudEventStreamObserver(
                        eventHandler::handleEvent,
                        e -> requestReconnection(),
                        () -> observerStateTracker.trackObserverStateChange(ObserverState.DISCONNECTED)
                )
        );
        observerStateTracker.trackObserverStateChange(ObserverState.CONNECTED);

        final CloudEvent joinEvent = createJoinEvent(
                UUID.randomUUID().toString(),
                eventHandler.getSupportedTags()
        );

        sendEvent(newObserver, joinEvent);
        observerStateTracker.trackObserverStateChange(ObserverState.AWAITS_GREET);
        return newObserver;
    }

    @Override
    public void sendEvent(final CloudEvent event) {
        sendEvent(streamObserver, event);
    }

    private synchronized void sendEvent(
            final StreamObserver<CloudEvent> streamObserver,
            final CloudEvent event
    ) {
        eventTracker.trackEventSent(event);
        log.debug("Cached sent event with ID: {}", event.getId());

        streamObserver.onNext(event);
    }

    private void requestReconnection() {
        reconnectionStrategy.requestReconnection(() -> {
            if (streamObserver != null) {
                streamObserver.onCompleted();
            }
            streamObserver = connect();
            return null;
        });
    }
}
