package com.java_template.common.grpc.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.protobuf.InvalidProtocolBufferException;
import com.java_template.common.auth.Authentication;
import com.java_template.common.config.Config;
import com.java_template.common.util.SslUtils;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.data.PojoCloudEventData;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.protobuf.ProtobufFormat;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.cyoda.cloud.api.event.processing.*;
import org.cyoda.cloud.api.grpc.CloudEventsServiceGrpc;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.java_template.common.config.Config.*;

//@Component
public class CyodaCalculationMemberClient implements DisposableBean, InitializingBean {
    public static final int SENT_EVENTS_CACHE_MAX_SIZE = 100;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final Authentication authentication;
    private ManagedChannel managedChannel;
    private CloudEventsServiceGrpc.CloudEventsServiceStub cloudEventsServiceStub;
    private StreamObserver<CloudEvent> cloudEventStreamObserver;
    private EventFormat eventFormat;
    private final ObjectMapper objectMapper;
    private final List<EventHandlingStrategy<? extends BaseEvent>> eventHandlingStrategies;
    private GrpcConnectionMonitor connectionMonitor;

    private static final List<String> TAGS = List.of(GRPC_PROCESSOR_TAG);
    private static final String SOURCE = "urn:cyoda:calculation-member:" + Config.GRPC_PROCESSOR_TAG;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int MAX_RETRIES = 10;

    private final AtomicReference<MemberStatus> memberStatusRef = new AtomicReference<>(MemberStatus.starting());

    public record EventAndTrigger(BaseEvent baseEvent, CloudEventType triggerEvent) {}

    // Cache for tracking sent events by their ID for correlation with acknowledgments, so that we can monitor that
    // we are actually getting ACKs from the stuff we send out
    private final Cache<String, EventAndTrigger> sentEventsCache = setupSentEventsCache();

    @NotNull
    static Cache<String, EventAndTrigger> setupSentEventsCache() {
        return Caffeine.newBuilder()
                .maximumSize(SENT_EVENTS_CACHE_MAX_SIZE)
                .expireAfterWrite(
                        5,
                        TimeUnit.MINUTES
                )
                .build();
    }

    // There is member information in the GREET event. Keeping it, in case we need it later.
    private CalculationMemberGreetEvent greetEvent = null;

    public CyodaCalculationMemberClient(
            ObjectMapper objectMapper,
            List<EventHandlingStrategy<? extends BaseEvent>> eventHandlingStrategies,
            Authentication authentication
    ) {
        this.objectMapper = objectMapper;
        this.authentication = authentication;
        this.eventHandlingStrategies = eventHandlingStrategies;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            log.info(
                    "Initializing gRPC client with address: {}:{}",
                    GRPC_ADDRESS,
                    GRPC_SERVER_PORT
            );
            log.debug(
                    "SSL trusted hosts: {}",
                    Config.getTrustedHosts()
            );
            log.debug(
                    "SSL trust all: {}",
                    Config.SSL_TRUST_ALL
            );

            managedChannel = SslUtils.createGrpcChannelBuilder(
                    GRPC_ADDRESS,
                    GRPC_SERVER_PORT
            ).build();

            log.debug(
                    "Created gRPC channel with authority: {}",
                    managedChannel.authority()
            );

            // Create a dynamic auth interceptor that gets fresh tokens when old ones expire, so that we never run out.
            ClientInterceptor authInterceptor = new ClientAuthorizationInterceptor(authentication);

            cloudEventsServiceStub = CloudEventsServiceGrpc.newStub(managedChannel)
                    .withWaitForReady()
                    .withInterceptors(authInterceptor);

            eventFormat = EventFormatProvider.getInstance().resolveFormat(ProtobufFormat.PROTO_CONTENT_TYPE);
            if (eventFormat == null) {
                throw new IllegalStateException("Unable to resolve protobuf event format for " + ProtobufFormat.PROTO_CONTENT_TYPE);
            }

            connectionMonitor = new GrpcConnectionMonitor(
                    managedChannel,
                    memberStatusRef,
                    new GrpcConnectionMonitor.StreamObserverProvider() {
                        @Override
                        public StreamObserver<CloudEvent> getCurrentStreamObserver() {
                            return cloudEventStreamObserver;
                        }

                        @Override
                        public void clearStreamObserver() {
                            cloudEventStreamObserver = null;
                        }
                    },
                    sentEventsCache
            );
            connectionMonitor.startMonitoring();

            log.info(
                    "gRPC client initialized successfully with server address: {} and port: {}",
                    GRPC_ADDRESS,
                    GRPC_SERVER_PORT
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to initialize gRPC client",
                    e
            );
        }
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();

        // Stop connection monitoring
        if (connectionMonitor != null) {
            connectionMonitor.stopMonitoring();
        }

        if (cloudEventStreamObserver != null) {
            cloudEventStreamObserver.onCompleted();
        }
        if (managedChannel != null) {
            try {
                managedChannel.shutdown()
                        .awaitTermination(
                                100,
                                TimeUnit.SECONDS
                        );
                if (!managedChannel.isTerminated()) {
                    log.warn("Forcing gRPC channel shutdown");
                    managedChannel.shutdownNow();
                }
                log.info("gRPC channel shut down successfully");
            } catch (InterruptedException e) {
                log.error(
                        "Interrupted while shutting down gRPC channel",
                        e
                );
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unused")
    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        try {
            connectToStream();
        } catch (Exception e) {
            log.error(
                    "Failed to connect to gRPC stream. Scheduling Reconnect",
                    e
            );
            scheduleReconnect();
        }
    }

    private void connectToStream() {
        if (cloudEventStreamObserver != null) {
            cloudEventStreamObserver.onCompleted();
        }

        try {
            cloudEventStreamObserver = cloudEventsServiceStub.startStreaming(new StreamObserver<>() {
                @Override
                public void onNext(CloudEvent cloudEvent) {
                    handleCloudEvent(cloudEvent);
                }

                @Override
                public void onError(Throwable t) {
                    log.error(
                            "gRPC stream error - connection lost to remote backend. Error type: {}, message: {}",
                            t.getClass().getSimpleName(),
                            t.getMessage(),
                            t
                    );
                    scheduleReconnect();
                }

                @Override
                public void onCompleted() {
                    log.warn("Stream completed by remote backend, scheduling reconnect...");
                    scheduleReconnect();
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        log.info("Connected to gRPC event stream and awaiting events...");
        sendJoinEvent();
    }

    private void scheduleReconnect() {
        MemberStatus currentStatus = memberStatusRef.get();
        int currentRetries = currentStatus.retryCount();

        if (currentRetries >= MAX_RETRIES) {
            memberStatusRef.updateAndGet(MemberStatus::offline);
            log.error(
                    "Maximum reconnection attempts ({}) reached. Member status updated to OFFLINE. Giving up.",
                    MAX_RETRIES
            );
            return;
        }

        int delaySeconds = (int) Math.pow(
                2,
                currentRetries
        );
        MemberStatus updatedStatus = memberStatusRef.updateAndGet(MemberStatus::withIncrementedRetryCount);
        log.info(
                "Attempting to reconnect to gRPC stream in {} seconds (retry attempt {}) - current member status: {}",
                delaySeconds,
                updatedStatus.retryCount(),
                updatedStatus.state()
        );
        scheduler.schedule(
                this::connectToStream,
                delaySeconds,
                TimeUnit.SECONDS
        );
        log.info(
                "Reconnect scheduled in {} seconds",
                delaySeconds
        );
    }

    private void sendJoinEvent() {
        CalculationMemberJoinEvent event = new CalculationMemberJoinEvent();
        event.setTags(TAGS);
        String joinEventId = UUID.randomUUID().toString();
        event.setId(joinEventId);

        memberStatusRef.updateAndGet(status -> status.joiningWithEventId(joinEventId));
        log.info(
                "Member status updated to JOINING with join event ID: {}",
                joinEventId
        );

        try {
            sendEvent(
                    event,
                    CloudEventType.CALCULATION_MEMBER_JOIN_EVENT
            );
        } catch (Exception e) {
            log.error(
                    "Failed to send join event. Will schedule a reconnect.",
                    e
            );
            scheduleReconnect();
        }
    }

    private void handleCloudEvent(CloudEvent cloudEvent) {
        CompletableFuture.runAsync(() -> {
            try {
                CloudEventType cloudEventType = CloudEventType.fromValue(cloudEvent.getType());
                eventHandlingStrategies.stream()
                        .filter(strategy -> strategy.supports(cloudEventType))
                        .findFirst()
                        .ifPresentOrElse(
                                strategy -> {
                                    log.debug(
                                            "Using strategy '{}' for event type '{}'",
                                            strategy.getClass().getSimpleName(),
                                            cloudEventType
                                    );

                                    // The contract on handleEvent is that it does not throw Exceptions,
                                    // but handles them internally and returns an error response.
                                    final var response = strategy.handleEvent(cloudEvent);
                                    if (response != null) {
                                        sendEvent(
                                                response,
                                                cloudEventType
                                        );
                                    }
                                },
                                () -> handleOtherEvents(cloudEvent)
                        );
            } catch (Exception e) {
                log.error(
                        "Error processing event: {}",
                        cloudEvent,
                        e
                );
            }
        });
    }

    /**
     * Handles event types that don't require specific strategies.
     */
    private void handleOtherEvents(CloudEvent cloudEvent) {
        CloudEventType cloudEventType = CloudEventType.fromValue(cloudEvent.getType());
        log.debug(
                "[IN] Received event {}: \n{}",
                cloudEventType,
                cloudEvent.getTextData()
        );
        switch (cloudEventType) {
            case CloudEventType.EVENT_ACK_RESPONSE -> {
                Optional<EventAckResponse> response = parseCloudEvent(
                        cloudEvent,
                        EventAckResponse.class
                );
                response.ifPresent(eventAckResponse -> {
                    String sourceEventId = eventAckResponse.getSourceEventId();
                    boolean success = eventAckResponse.getSuccess();

                    // Check if this is an acknowledgment for our join event
                    MemberStatus currentStatus = memberStatusRef.get();
                    if (sourceEventId != null && sourceEventId.equals(currentStatus.lastJoinEventId())) {
                        if (success) {
                            memberStatusRef.updateAndGet(MemberStatus::joined);
                            log.info(
                                    "Member status updated to JOINED - successfully joined calculation network (event ID: {})",
                                    sourceEventId
                            );
                        } else {
                            memberStatusRef.updateAndGet(MemberStatus::rejected);
                            log.warn(
                                    "Member status updated to REJECTED - join request was rejected (event ID: {})",
                                    sourceEventId
                            );
                            // Schedule another join attempt
                            scheduleReconnect();
                        }
                    } else {
                        if (sourceEventId != null) {
                            // Handle other event acknowledgments
                            // Try to find and remove the correlated outgoing event from cache
                            EventAndTrigger cachedEvent = sentEventsCache.getIfPresent(sourceEventId);
                            if (cachedEvent != null) {
                                sentEventsCache.invalidate(sourceEventId);
                                if (log.isDebugEnabled()) {
                                    long estimatedSize = sentEventsCache.estimatedSize();
                                    log.debug(
                                            "Removed {} with {} from cache. There are {} events left in cache.",
                                            cachedEvent.triggerEvent(),
                                            success ? "ACK" : "NACK",
                                            estimatedSize
                                    );
                                }
                            } else {
                                log.warn(
                                        "ACK for event ID: {} not found in cache",
                                        sourceEventId
                                );
                            }
                            if (success) {
                                log.debug(
                                        "Received event ACK for event ID: {}",
                                        sourceEventId
                                );
                            } else {
                                throw new IllegalStateException("Received event NACK for event ID: " + sourceEventId);
                            }
                        }
                    }
                });
            }
            case CloudEventType.CALCULATION_MEMBER_GREET_EVENT -> {
                log.debug(
                        "[IN] Received event {}: \n{}",
                        cloudEventType,
                        cloudEvent.getTextData()
                );
                parseCloudEvent(
                        cloudEvent,
                        CalculationMemberGreetEvent.class
                ).ifPresent(greetEvent -> {
                    this.greetEvent = greetEvent;
                    memberStatusRef.updateAndGet(MemberStatus::joined);
                    log.info(
                            "Received greet event: {}",
                            this.greetEvent
                    );
                });
            }
            case CloudEventType.CALCULATION_MEMBER_KEEP_ALIVE_EVENT -> {
                Optional<EventAckResponse> response = parseCloudEvent(
                        cloudEvent,
                        EventAckResponse.class
                );
                response.ifPresent(eventAckResponse -> {
                    eventAckResponse.setSourceEventId(eventAckResponse.getId());
                    memberStatusRef.updateAndGet(status -> status.withKeepAliveTimestamp(System.currentTimeMillis()));
                    sendEvent(
                            eventAckResponse,
                            CloudEventType.CALCULATION_MEMBER_KEEP_ALIVE_EVENT
                    );
                });
            }
            default -> log.warn(
                    "[IN] Received UNHANDLED event type {}: \n{}",
                    cloudEventType,
                    cloudEvent.getTextData()
            );
        }
    }

    @SuppressWarnings("SameParameterValue")
    private <T extends BaseEvent> Optional<T> parseCloudEvent(
            CloudEvent cloudEvent,
            Class<T> clazz
    ) {
        try {
            return Optional.of(objectMapper.readValue(
                    cloudEvent.getTextData(),
                    clazz
            ));
        } catch (JsonProcessingException e) {
            log.error(
                    "Error parsing cloud event. This shouldn't happen unless the systems are misaligned {}",
                    cloudEvent,
                    e
            );
            return Optional.empty();
        }
    }

    public void sendEvent(
            BaseEvent event,
            CloudEventType cloudEventType
    ) {
        CloudEvent cloudEvent;
        try {
            cloudEvent = CloudEvent.parseFrom(
                    eventFormat.serialize(
                            CloudEventBuilder.v1()
                                    .withType(event.getClass().getSimpleName())
                                    .withSource(URI.create(SOURCE))
                                    .withId(UUID.randomUUID().toString())
                                    .withData(
                                            PojoCloudEventData.wrap(
                                                    event,
                                                    eventData -> {
                                                        try {
                                                            return objectMapper.writeValueAsBytes(eventData);
                                                        } catch (JsonProcessingException e) {
                                                            throw new RuntimeException(
                                                                    "Error serializing event data",
                                                                    e
                                                            );
                                                        }
                                                    }
                                            )
                                    )
                                    .build()
                    )
            );
        } catch (InvalidProtocolBufferException e) {
            log.error(
                    "Failed to parse cloud event",
                    e
            );
            // TODO: Define the strategy for handling serialization errors. For now we just log it.
            return;
        }

        var observer = cloudEventStreamObserver;

        if (observer == null) {
            throw new IllegalStateException("Stream observer is not initialized");
        }

        if (event.getSuccess()) {
            log.debug(
                    "[OUT] Sending event {}, success: {}",
                    cloudEvent.getType(),
                    event.getSuccess()
            );
        } else {
            log.warn(
                    "[OUT] Sending event {}, success: {}",
                    cloudEvent.getType(),
                    event.getSuccess()
            );
        }

        // Cache the event for correlation with acknowledgments.
        // Join events are not ACKED directly, we get a GREET event instead.
        if (event.getId() != null && cloudEventType != CloudEventType.CALCULATION_MEMBER_JOIN_EVENT) {
            sentEventsCache.put(
                    event.getId(),
                    new EventAndTrigger(
                            event,
                            cloudEventType
                    )
            );
            log.debug(
                    "Cached sent event with ID: {}",
                    event.getId()
            );
        }

        // StreamObserver is not thread-safe. Current implementation uses synchronized block.
        // For high-concurrency production environments, consider using event queue or stream pooling.
        synchronized (observer) {
            observer.onNext(cloudEvent);
        }
    }

    public MemberStatus getMemberStatus() {
        return memberStatusRef.get();
    }
}
