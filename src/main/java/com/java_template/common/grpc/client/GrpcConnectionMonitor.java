package com.java_template.common.grpc.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.java_template.common.config.Config;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ABOUTME: Monitors gRPC connection state and member status, providing periodic health checks and state logging.
 * Handles connection state changes, keep-alive monitoring, and automatic cleanup for offline members.
 */
public class GrpcConnectionMonitor {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ManagedChannel managedChannel;
    private final AtomicReference<MemberStatus> memberStatusRef;
    private final StreamObserverProvider streamObserverProvider;
    private final Cache<String, CyodaCalculationMemberClient.EventAndTrigger> sentEventsCache;
    private ScheduledExecutorService monitorExecutor;

    /**
     * Interface for providing access to the current stream observer.
     */
    public interface StreamObserverProvider {
        /**
         * Gets the current stream observer, may be null if disconnected.
         */
        StreamObserver<CloudEvent> getCurrentStreamObserver();

        /**
         * Clears the current stream observer (sets it to null).
         */
        void clearStreamObserver();
    }

    /**
     * Creates a new gRPC connection monitor.
     *
     * @param managedChannel         the gRPC managed channel to monitor
     * @param memberStatusRef        atomic reference to the member status
     * @param streamObserverProvider provider for accessing the current stream observer
     * @param sentEventsCache        the Cache of sent events. This should not grow.
     */
    public GrpcConnectionMonitor(ManagedChannel managedChannel,
                                 AtomicReference<MemberStatus> memberStatusRef,
                                 StreamObserverProvider streamObserverProvider,
                                 Cache<String, CyodaCalculationMemberClient.EventAndTrigger> sentEventsCache
    ) {
        this.managedChannel = managedChannel;
        this.memberStatusRef = memberStatusRef;
        this.streamObserverProvider = streamObserverProvider;
        this.sentEventsCache = sentEventsCache;
    }

    /**
     * Starts monitoring the gRPC connection state.
     */
    public void startMonitoring() {
        if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
            logger.warn("Connection monitoring is already running");
            return;
        }

        monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gRPC-Connection-Monitor");
            t.setDaemon(true);
            return t;
        });

        // Monitor connection state every 30 seconds
        monitorExecutor.scheduleWithFixedDelay(this::monitorConnection, 10, 30, TimeUnit.SECONDS);
        logger.info("Started gRPC connection monitoring");
    }

    /**
     * Stops monitoring the gRPC connection state.
     */
    public void stopMonitoring() {
        if (monitorExecutor != null && !monitorExecutor.isShutdown()) {
            monitorExecutor.shutdown();
            try {
                if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    monitorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                monitorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("Stopped gRPC connection monitoring");
        }
    }

    /**
     * Performs a single monitoring check of the connection state.
     */
    private void monitorConnection() {
        try {
            ConnectivityState currentState = managedChannel.getState(false);
            MemberStatus currentMemberStatus = memberStatusRef.get();
            ConnectivityState lastKnownState = currentMemberStatus.lastKnownState();

            if (currentState != lastKnownState) {
                logger.info("gRPC connection state changed: {} -> {} (member status: {})",
                        lastKnownState, currentState, currentMemberStatus.state());
                // Update the connectivity state atomically
                memberStatusRef.updateAndGet(status -> status.withConnectivityState(currentState));

                logConnectionStateChange(currentState, currentMemberStatus);
            }

            // If member status is OFFLINE, initiate clean shutdown
            if (currentMemberStatus.state() == MemberStatus.MemberState.OFFLINE) {
                handleOfflineState();
                return; // Exit monitoring loop early
            }

            // Check if stream observer is null (disconnected)
            checkStreamObserverState(currentState, currentMemberStatus);

            // Log periodic status for debugging
            logPeriodicStatus(currentState, currentMemberStatus);

            // Log warning if keep alive isn't coming
            checkKeepAliveStatus(currentMemberStatus);

            // Log warning if sentEventsCache is growing unchecked
            checkSentEventsCacheSize();

        } catch (Exception e) {
            logger.error("Error monitoring gRPC connection state", e);
        }
    }

    private void checkSentEventsCacheSize() {
        if (sentEventsCache.estimatedSize() > CyodaCalculationMemberClient.SENT_EVENTS_CACHE_MAX_SIZE/10) {
            logger.warn("Sent events cache is growing. Current size: {}", sentEventsCache.estimatedSize());
        } else if (sentEventsCache.estimatedSize() > CyodaCalculationMemberClient.SENT_EVENTS_CACHE_MAX_SIZE/2) {
            logger.error("Sent events cache is growing unchecked. Current size: {}", sentEventsCache.estimatedSize());
        } else {
            logger.debug("Sent events cache size: {}", sentEventsCache.estimatedSize());
        }
    }

    /**
     * Logs connection state changes with the appropriate detail.
     */
    private void logConnectionStateChange(ConnectivityState currentState, MemberStatus currentMemberStatus) {
        switch (currentState) {
            case READY:
                logger.info("gRPC connection is READY - connected to {}:{} (member status: {})",
                        Config.GRPC_ADDRESS, Config.GRPC_SERVER_PORT, currentMemberStatus.state());
                break;
            case CONNECTING:
                logger.info("gRPC connection is CONNECTING to {}:{} (member status: {})",
                        Config.GRPC_ADDRESS, Config.GRPC_SERVER_PORT, currentMemberStatus.state());
                break;
            case IDLE:
                logger.info("gRPC connection is IDLE (member status: {})", currentMemberStatus.state());
                break;
            case TRANSIENT_FAILURE:
                logger.warn("gRPC connection is in TRANSIENT_FAILURE state - connection issues detected (member status: {})",
                        currentMemberStatus.state());
                logger.warn("Attempting to connect to: {}:{}", Config.GRPC_ADDRESS, Config.GRPC_SERVER_PORT);
                logger.warn("SSL trusted hosts: {}", Config.getTrustedHosts());
                logger.warn("Channel authority: {}", managedChannel.authority());
                break;
            case SHUTDOWN:
                logger.warn("gRPC connection is SHUTDOWN (member status: {})", currentMemberStatus.state());
                break;
        }
    }

    /**
     * Handles the offline state by cleaning up stream observer.
     */
    private void handleOfflineState() {
        logger.warn("Member status is OFFLINE. Cannot recover from this state. You need to restart this node.");
        // Note: We don't call destroy() here to avoid recursive shutdown
        StreamObserver<CloudEvent> streamObserver = streamObserverProvider.getCurrentStreamObserver();
        if (streamObserver != null) {
            logger.warn("Initiating shutdown of gRPC stream observer due to OFFLINE status");
            try {
                streamObserver.onCompleted();
                streamObserverProvider.clearStreamObserver();
            } catch (Exception e) {
                logger.warn("Error completing stream observer during OFFLINE shutdown", e);
            }
        }
    }

    /**
     * Checks the state of the stream observer relative to the connection state.
     */
    private void checkStreamObserverState(ConnectivityState currentState, MemberStatus currentMemberStatus) {
        Object streamObserver = streamObserverProvider.getCurrentStreamObserver();
        if (streamObserver == null && currentState == ConnectivityState.READY) {
            logger.warn("gRPC channel is READY but stream observer is null - connection may have been lost (member status: {})",
                    currentMemberStatus.state());
        }
    }

    /**
     * Logs periodic status information for debugging.
     */
    private void logPeriodicStatus(ConnectivityState currentState, MemberStatus currentMemberStatus) {
        Object streamObserver = streamObserverProvider.getCurrentStreamObserver();
        String logMsgTemplate = "gRPC connection status: state={}, streamObserver={}, memberStatus={}";
        String streamObserverState = streamObserver != null ? "connected" : "disconnected";

        if (currentState == ConnectivityState.READY || currentState == ConnectivityState.IDLE) {
            logger.debug(logMsgTemplate, currentState, streamObserverState, currentMemberStatus.state());
        } else {
            logger.warn(logMsgTemplate, currentState, streamObserverState, currentMemberStatus.state());
        }
    }

    /**
     * Checks keep-alive status and logs warnings if stale.
     */
    private void checkKeepAliveStatus(MemberStatus currentMemberStatus) {
        long timeSinceLastKeepAlive = System.currentTimeMillis() - currentMemberStatus.lastKeepAliveTimestamp();
        if (timeSinceLastKeepAlive > Config.KEEP_ALIVE_WARNING_THRESHOLD) {
            logger.warn("Last keep alive received {} seconds ago. Connection might be stale. (member status: {})",
                    timeSinceLastKeepAlive / 1000, currentMemberStatus.state());
        }
    }

    /**
     * Checks if monitoring is currently active.
     *
     * @return true if monitoring is active, false otherwise
     */
    public boolean isMonitoring() {
        return monitorExecutor != null && !monitorExecutor.isShutdown();
    }
}
