package com.java_template.common.grpc.client.monitoring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.ConnectivityState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.cyoda.cloud.api.event.processing.EventAckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static com.java_template.common.config.Config.KEEP_ALIVE_WARNING_THRESHOLD;
import static com.java_template.common.config.Config.MONITORING_SCHEDULER_DELAY_SECONDS;
import static com.java_template.common.config.Config.MONITORING_SCHEDULER_INITIAL_DELAY_SECONDS;
import static com.java_template.common.config.Config.SENT_EVENTS_CACHE_MAX_SIZE;

@Component
class GrpcConnectionMonitor implements EventTracker, ConnectionStateTracker {
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
            > monitoringEventListeners;

    public GrpcConnectionMonitor(final List<MonitoringEventListener<MonitoringEvent>> monitoringEventListeners) {
        this.monitoringEventListeners = monitoringEventListeners.stream().collect(
                Collectors.groupingBy(
                        MonitoringEventListener::getEventType,
                        Collectors.toList()
                )
        );
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
        logger.info("Stopping monitoring...");
        monitorExecutor.shutdown();
        logger.info("Monitoring stoped");
    }

    private void monitor() {
        checkSentEventsCacheSize();
        checkTimeSinceLastKeepAlive();
    }

    private static final Set<String> EVENT_TYPES_TO_IGNORE = Set.of(
                    CloudEventType.EVENT_ACK_RESPONSE,
                    CloudEventType.CALCULATION_MEMBER_JOIN_EVENT
            ).stream()
            .map(Enum::toString)
            .collect(Collectors.toSet());

    @Override
    public void trackEventSent(final CloudEvent cloudEvent) {
        if (EVENT_TYPES_TO_IGNORE.stream().noneMatch(eventType -> eventType.equals(cloudEvent.getType()))) {
            sentEventsCache.put(cloudEvent.getId(), cloudEvent);
            logger.debug("Cached sent event '{}':'{}'", cloudEvent.getType(), cloudEvent.getId());
            broadcastMonitoringEvent(
                    new EventSent(cloudEvent.getId(), cloudEvent.getType())
            );
        }
    }

    @Override
    public void trackAcknowledgeReceived(final EventAckResponse acknowledgeResponse) {
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
                }
            } else {
                logger.debug("Event '{}' for received '{}' is not found", sourceEventId, success ? "ACK" : "NACK");
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

    public void trackConnectionStateChanged(
            final Supplier<ConnectivityState> newStateProvider,
            final BiConsumer<ConnectivityState, Runnable> initNextListener
    ) {
        final var newState = newStateProvider.get();
        initNextListener.accept(newState, () -> trackConnectionStateChanged(newStateProvider, initNextListener));
        broadcastMonitoringEvent(new GrpcConnectionStateChangedEvent(newState));

        final var oldState = this.lastConnectionState.getAndSet(newState);
        logger.info(
                "gRPC connection state changed: {} -> {} (member status: {})",
                oldState,
                newState,
                lastObserverState.get()
        );
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
            logger.warn(
                    "Keep alive not received yet (Connection state: {}; Member state: {})",
                    lastConnectionState.get(),
                    lastObserverState.get()
            );
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
        logger.info(
                "Observer state changes from {} to {} (connection state: {})",
                oldState,
                newState,
                lastConnectionState.get()
        );
        broadcastMonitoringEvent(new StreamObserverStateChangedEvent(newState));
    }

    private void broadcastMonitoringEvent(final MonitoringEvent monitoringEvent) {
        if (!monitoringEventListeners.containsKey(monitoringEvent.getClass())) {
            return;
        }
        for (final var monitoringEventListener : monitoringEventListeners.get(monitoringEvent.getClass())) {
            monitoringEventListener.handle(monitoringEvent);
        }
    }
}

