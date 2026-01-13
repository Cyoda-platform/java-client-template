package com.java_template.common.grpc.client.monitoring;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.java_template.common.config.Config;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.ConnectivityState;
import org.cyoda.cloud.api.event.common.CloudEventType;
import org.cyoda.cloud.api.event.processing.EventAckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * ABOUTME: Central monitoring component for gRPC connection health, event tracking,
 * and performance metrics with caching and listener notification capabilities.
 */
@Component
public class GrpcConnectionMonitor implements EventTracker, ConnectionStateTracker {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final AtomicLong lastKeepAliveTimestampMs = new AtomicLong(-1);
    private final AtomicReference<ConnectivityState> lastConnectionState = new AtomicReference<>(ConnectivityState.SHUTDOWN);
    private final AtomicReference<ObserverState> lastObserverState = new AtomicReference<>(ObserverState.DISCONNECTED);

    private final ScheduledExecutorService monitorExecutor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("gRPC-Connection-Monitor")
            .daemon(true)
            .factory()
    );

    private final Config config;
    private final Cache<String, CloudEvent> sentEventsCache;

    public GrpcConnectionMonitor(Config config) {
        this.config = config;
        this.sentEventsCache = Caffeine.newBuilder()
                .maximumSize(config.getSentEventsCacheMaxSize())
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    @PostConstruct
    private void init() {
        monitorExecutor.scheduleWithFixedDelay(
                this::monitor,
                config.getMonitoringSchedulerInitialDelaySeconds(),
                config.getMonitoringSchedulerDelaySeconds(),
                TimeUnit.SECONDS
        );
    }

    @PreDestroy
    private void shutdown() {
        logger.info("Stopping monitoring...");
        monitorExecutor.shutdown();
        logger.info("Monitoring stopped");
    }

    private void monitor() {

        if (lastObserverState.get() == ObserverState.IDLE) {
            logger.debug("Monitoring paused - system is IDLE");
            return;
        }
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

    }

    @Override
    public void trackKeepAlive(final Long eventTimestamp) {
        lastKeepAliveTimestampMs.set(eventTimestamp);
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

        final var oldState = this.lastConnectionState.getAndSet(newState);

        logger.info(
                "gRPC Managed Channel state changed: {} -> {} (stream observer state: {})",
                oldState,
                newState,
                lastObserverState.get()
        );
    }

    private void checkSentEventsCacheSize() {
        int maxSize = config.getSentEventsCacheMaxSize();
        if (sentEventsCache.estimatedSize() > maxSize / 10) {
            logger.warn("Sent events cache is growing. Current size: {}", sentEventsCache.estimatedSize());
        } else if (sentEventsCache.estimatedSize() > maxSize / 2) {
            logger.error("Sent events cache is growing unchecked. Current size: {}", sentEventsCache.estimatedSize());
        } else {
            logger.debug("Sent events cache size: {}", sentEventsCache.estimatedSize());
        }
    }

    private void checkTimeSinceLastKeepAlive() {
        final var lastKeepAliveTimestampMs = this.lastKeepAliveTimestampMs.get();
        if (lastKeepAliveTimestampMs < 0) {
            logger.warn(
                    "Keep alive not received yet (Managed Channel state: {}; Stream Observer state: {})",
                    lastConnectionState.get(),
                    lastObserverState.get()
            );
            return;
        }

        final var timeSinceLastKeepAlive = System.currentTimeMillis() - lastKeepAliveTimestampMs;
        logger.debug("{}ms since last keep alive", timeSinceLastKeepAlive);

        long threshold = config.getKeepAliveWarningThreshold();
        if (timeSinceLastKeepAlive > threshold) {
            logger.warn(
                    "No Keep alive received within the {}ms threshold. Last successful was {}ms ago. (Managed Channel state: {}; Stream Observer state: {})",
                    threshold,
                    timeSinceLastKeepAlive,
                    lastConnectionState.get(),
                    lastObserverState.get()
            );
        }
    }

    @Override
    public void trackObserverStateChange(final ObserverState newState) {
        final var oldState = lastObserverState.getAndSet(newState);
        logger.info(
                "Stream Observer state changes: {} -> {} (managed channel state: {})",
                oldState,
                newState,
                lastConnectionState.get()
        );
    }

    @Override
    public ObserverState getLastObserverState() {
        return lastObserverState.get();
    }

    public record GrpcMonitoringState(
            ConnectivityState connectionState,
            ObserverState observerState,
            long lastKeepAliveTimestampMs
    ) {}

    public GrpcMonitoringState getLastKnownState() {
        return new GrpcMonitoringState(
                lastConnectionState.get(),
                lastObserverState.get(),
                lastKeepAliveTimestampMs.get()
        );
    }
}

