package com.java_template.common.grpc.client_v2;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.cyoda.cloud.api.event.processing.EventAckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class GrpcConnectionMonitor implements EventTracker, ObserverStateTracker {
    public static final int SENT_EVENTS_CACHE_MAX_SIZE = 100; // TODO: Move to props
    public static final int MONITORING_SCHEDULER_INITIAL_DELAY_SECONDS = 10;  // TODO: Move to props
    public static final int MONITORING_SCHEDULER_DELAY_SECONDS = 30; // TODO: Move to props
    public static final long KEEP_ALIVE_WARNING_THRESHOLD = 300; // TODO: Move to props

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final AtomicLong lastKeepAliveTimestampMs = new AtomicLong(-1);
    private final AtomicReference<ConnectivityState> lastConnectionState = new AtomicReference<>(ConnectivityState.SHUTDOWN);
    private final AtomicReference<ObserverState> lastObserverState = new AtomicReference<>(ObserverState.DISCONNECTED);

    private final ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("gRPC-Connection-Monitor")
            .daemon(true)
            .factory()
    );

    private final Cache<String, CloudEvent> sentEventsCache = Caffeine.newBuilder()
            .maximumSize(SENT_EVENTS_CACHE_MAX_SIZE)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private final Map<
            Class<? extends MonitoringEvent>,
            List<MonitoringEventListener<MonitoringEvent>>
            > monitoringEventListeners = new ConcurrentHashMap<>();

    private final ManagedChannel managedChannel;

    public GrpcConnectionMonitor(
            final List<MonitoringEventListener<MonitoringEvent>> monitoringEventListeners,
            final ManagedChannel managedChannel
    ) {
        for (final var listener : monitoringEventListeners) {
            this.monitoringEventListeners.computeIfAbsent(
                    listener.getEventType(),
                    k -> new ArrayList<>()
            ).add(listener);
        }

        this.managedChannel = managedChannel;
    }

    @PostConstruct
    private void init() {
        monitorExecutor.scheduleWithFixedDelay(
                this::monitor,
                MONITORING_SCHEDULER_INITIAL_DELAY_SECONDS,
                MONITORING_SCHEDULER_DELAY_SECONDS,
                TimeUnit.SECONDS
        );
    }

    @PreDestroy
    private void shutdown() {
        monitorExecutor.shutdown();
    }

    private void monitor() {
        logConnectionState();
        checkSentEventsCacheSize();
        checkTimeSinceLastKeepAlive();
    }

    @Override
    public void trackEventSent(final CloudEvent cloudEvent) {
        sentEventsCache.put(cloudEvent.getId(), cloudEvent);
        broadcastMonitoringEvent(new EventSent(cloudEvent.getId(), cloudEvent.getType()));
    }

    @Override
    public void trackAcknowledge(final EventAckResponse acknowledgeResponse) {
        final var ackId = acknowledgeResponse.getId();
        final var sourceEventId = acknowledgeResponse.getSourceEventId();
        final var success = acknowledgeResponse.getSuccess();

        if (sourceEventId != null) {
            final var cachedEvent = sentEventsCache.getIfPresent(sourceEventId);
            if (cachedEvent != null) {
                sentEventsCache.invalidate(acknowledgeResponse.getSourceEventId());
                if (logger.isDebugEnabled()) {
                    final var estimatedSize = sentEventsCache.estimatedSize();
                    logger.debug(
                            "Removed {} with {} from cache. There are {} events left in cache.",
                            cachedEvent.getType(),
                            success ? "ACK" : "NACK",
                            estimatedSize
                    );
                } else {
                    logger.warn("Event with Id: {} not found in cache", sourceEventId);
                }
            }
        } else {
            logger.warn(
                    "Received {} event with id {} without source event id",
                    success ? "ACK" : "NACK",
                    ackId
            );
        }

        broadcastMonitoringEvent(new AckReceivedEvent(ackId, sourceEventId, success));
    }

    @Override
    public void trackKeepAlive(final Long eventTimestamp) {
        lastKeepAliveTimestampMs.set(eventTimestamp);
        broadcastMonitoringEvent(new KeepAliveReceivedEvent(eventTimestamp));
    }

    @Override
    public void trackGreetReceived() {
        trackObserverStateChange(ObserverState.READY);
    }

    private void logConnectionState() {
        final var lastConnectionState = this.lastConnectionState.get();
        final var currentConnectionState = managedChannel.getState(false);
        if (lastConnectionState != currentConnectionState) {
            this.lastConnectionState.set(currentConnectionState);
            logger.info(
                    "gRPC connection state changed: {} -> {} (member status: {})",
                    lastConnectionState,
                    currentConnectionState,
                    lastObserverState.get()
            );
        }
    }

    private void checkSentEventsCacheSize() {
        if (sentEventsCache.estimatedSize() > SENT_EVENTS_CACHE_MAX_SIZE / 10) {
            logger.warn("Sent events cache is growing. Current size: {}", sentEventsCache.estimatedSize());
        } else if (sentEventsCache.estimatedSize() > SENT_EVENTS_CACHE_MAX_SIZE / 2) {
            logger.error("Sent events cache is growing unchecked. Current size: {}", sentEventsCache.estimatedSize());
            broadcastMonitoringEvent(
                    new SentEventsWithoutAckGrowingEvent(
                            sentEventsCache.asMap()
                                    .values()
                                    .stream()
                                    .map(CloudEvent::getId)
                                    .toList()
                    )
            );
        } else {
            logger.debug("Sent events cache size: {}", sentEventsCache.estimatedSize());
        }
    }

    private void checkTimeSinceLastKeepAlive() {
        final var lastKeepAliveTimestampMs = this.lastKeepAliveTimestampMs.get();
        if (lastKeepAliveTimestampMs < 0) {
            logger.warn("Keep alive not received yet");
            return;
        }

        final var timeSinceLastKeepAlive = System.currentTimeMillis() - lastKeepAliveTimestampMs;
        logger.info("{}ms since last keep alive", timeSinceLastKeepAlive);

        if (timeSinceLastKeepAlive > KEEP_ALIVE_WARNING_THRESHOLD) {
            logger.warn(
                    "No Keep alive received within the {}ms threshold. Last successful was {}ms ago.",
                    KEEP_ALIVE_WARNING_THRESHOLD,
                    timeSinceLastKeepAlive
            );
        }
    }

    @Override
    public void trackObserverStateChange(final ObserverState newState) {
        final var oldState = lastObserverState.getAndSet(newState);
        logger.info("Connection state changes from {} to {}", oldState, newState);
        broadcastMonitoringEvent(new GrpcConnectionEvent(newState));
    }

    private void broadcastMonitoringEvent(final MonitoringEvent monitoringEvent) {
        for (final var monitoringEventListener : monitoringEventListeners.get(monitoringEvent.getClass())) {
            monitoringEventListener.handle(monitoringEvent);
        }
    }
}

enum ObserverState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AWAITS_GREET,
    READY,
}

interface MonitoringEventListener<MONITORING_EVENT_TYPE extends MonitoringEvent> {
    void handle(MONITORING_EVENT_TYPE monitoringEvent);

    Class<MONITORING_EVENT_TYPE> getEventType();
}

interface MonitoringEvent {}

record GrpcConnectionEvent(
        ObserverState connectionState
) implements MonitoringEvent {}

record EventSent(
        String eventId,
        String eventType
) implements MonitoringEvent {}

record AckReceivedEvent(
        String id,
        String sourceEventId,
        boolean success
) implements MonitoringEvent {}

record KeepAliveReceivedEvent(
        Long timestamp
) implements MonitoringEvent {}

record SentEventsWithoutAckGrowingEvent(
        List<String> events
) implements MonitoringEvent {}
