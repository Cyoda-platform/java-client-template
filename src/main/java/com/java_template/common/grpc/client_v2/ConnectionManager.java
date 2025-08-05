package com.java_template.common.grpc.client_v2;

import com.google.protobuf.InvalidProtocolBufferException;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.stub.StreamObserver;
import java.util.Set;
import java.util.UUID;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.cyoda.cloud.api.event.processing.CalculationMemberJoinEvent;
import org.cyoda.cloud.api.grpc.CloudEventsServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
class ConnectionManager implements EventSender {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final EventHandler eventHandler;
    private final EventTracker eventTracker;
    private final ConnectionStateTracker connectionStateTracker;
    private final CloudEventBuilder eventBuilder;
    private final CloudEventsServiceGrpc.CloudEventsServiceStub cloudEventsServiceStub;
    private final ReconnectionStrategy reconnectionStrategy;

    private StreamObserver<CloudEvent> streamObserver;

    public ConnectionManager(
            @Lazy final EventHandler eventHandler,
            final EventTracker eventTracker,
            final ConnectionStateTracker connectionStateTracker,
            final CloudEventBuilder eventBuilder,
            final CloudEventsServiceGrpc.CloudEventsServiceStub cloudEventsServiceStub,
            final ReconnectionStrategy reconnectionStrategy
    ) {
        this.eventHandler = eventHandler;
        this.eventTracker = eventTracker;
        this.connectionStateTracker = connectionStateTracker;
        this.eventBuilder = eventBuilder;
        this.cloudEventsServiceStub = cloudEventsServiceStub;
        this.reconnectionStrategy = reconnectionStrategy;
    }

    private CloudEvent createJoinEvent(
            final String id,
            final Set<String> tags
    ) throws InvalidProtocolBufferException {
        return eventBuilder.buildEvent(
                new CalculationMemberJoinEvent().withId(id).withTags(tags.stream().toList())
        );
    }

    @PostConstruct
    private void init() {
        try {
            initNewStreamObserver();
        } catch (Exception e) {
            log.error("Failed to connect", e);
            requestReconnection();
        }
    }

    @PreDestroy
    private void shutdown() {
        log.info("Stopping stream observer...");
        if (streamObserver != null) {
            streamObserver.onCompleted();
        }
        log.info("Stream observer stoped");
    }

    private StreamObserver<CloudEvent> connect() throws InvalidProtocolBufferException {
        connectionStateTracker.trackObserverStateChange(ObserverState.CONNECTING);

        final var newObserver = cloudEventsServiceStub.startStreaming(
                new CloudEventStreamObserver(
                        eventHandler::handleEvent,
                        e -> {
                            log.error("Stream observer error:", e);
                            requestReconnection();
                        },
                        () -> connectionStateTracker.trackObserverStateChange(ObserverState.DISCONNECTED)
                )
        );

        connectionStateTracker.trackObserverStateChange(ObserverState.JOINING);

        final var joinEvent = createJoinEvent(UUID.randomUUID().toString(), eventHandler.getSupportedTags());
        sendEvent(newObserver, joinEvent);

        connectionStateTracker.trackObserverStateChange(ObserverState.AWAITS_GREET);

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
            initNewStreamObserver();
            return null;
        });
    }

    private void initNewStreamObserver() throws InvalidProtocolBufferException {
        if (streamObserver != null) {
            streamObserver.onCompleted();
        }
        streamObserver = connect();
    }
}
