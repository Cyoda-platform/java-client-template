package com.java_template.common.grpc.client.connection;

import com.java_template.common.config.Config;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * ABOUTME: Default implementation of ReconnectionStrategy with exponential backoff,
 * retry limits, and scheduled reconnection attempts for gRPC connections.
 */
public class DefaultReconnectionStrategy implements ReconnectionStrategy {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger failedReconnectsCount = new AtomicInteger(0);

    private final AtomicBoolean isIdle = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<Consumer<Boolean>> idleStateListeners = new CopyOnWriteArrayList<>();
    private Runnable lastReconnectCallback;  // Store for resurrection

    private final Config config;

    public DefaultReconnectionStrategy(Config config) {
        this.config = config;
    }


    @PreDestroy
    public void shutdown() {
        log.info("Shutting down reconnection scheduler...");
        if (!reconnectionScheduler.isShutdown()) {
            reconnectionScheduler.shutdownNow();
        }
        log.info("Reconnection scheduler has been shutdown.");
    }

    @Override
    public void reset() {
        if (failedReconnectsCount.getAndSet(0) > 0) {
            log.info("Reconnection backoff counter has been reset");
        }
        if (isIdle.compareAndSet(true, false)) {
            notifyIdleStateListeners(false);
        }
    }

    private final AtomicBoolean hasReconnectAttempt = new AtomicBoolean(false);

    @Override
    public void requestReconnection(final Runnable reconnect) {
        log.info("Reconnect requested");

        this.lastReconnectCallback = reconnect;

        if (!hasReconnectAttempt.compareAndSet(false, true)) {
            log.info("Reconnect attempt already scheduled. Ignoring this request.");
            return;
        }

        final int attemptsCount = failedReconnectsCount.getAndIncrement();
        if (attemptsCount > config.getFailedReconnectsLimit()) {
            log.error("Failed reconnects limit ({}) reached. Giving up!!!", config.getFailedReconnectsLimit());
            hasReconnectAttempt.set(false);  // Must reset before returning
            enterIdleState();  // NEW: Transition to idle instead of just returning
            return;
        }

        final long reconnectDelayMs = calculateBackoff(attemptsCount);
        log.info("Scheduling reconnect attempt #{} with a {}ms delay.", attemptsCount, reconnectDelayMs);
        reconnectionScheduler.schedule(() -> {
            try {
                reconnect.run();
            } finally {
                hasReconnectAttempt.set(false);
            }
        }, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }

    private void enterIdleState() {
        if (isIdle.compareAndSet(false, true)) {
            hasReconnectAttempt.set(false);
            notifyIdleStateListeners(true);
            log.warn("Monitoring system is now IDLE. Call resurrect() to retry connection.");
        }
    }

    private void notifyIdleStateListeners(boolean b) {
        idleStateListeners.forEach(listener -> listener.accept(b));
    }


    @Override
    public void resurrect(final Runnable reconnect) {
        if (!isIdle.get()) {
            log.info("Not in idle state, resurrection not needed");
            return;
        }

        log.info("Resurrection requested - resetting counters and attempting reconnection");
        failedReconnectsCount.set(0);
        isIdle.set(false);
        notifyIdleStateListeners(false);

        // Use provided callback or stored one
        final Runnable callback = reconnect != null ? reconnect : lastReconnectCallback;
        if (callback != null) {
            requestReconnection(callback);
        }
    }

    @Override
    public void addIdleStateListener(Consumer<Boolean> listener) {
        idleStateListeners.add(listener);
    }

    private long calculateBackoff(final int attempt) {
        return Math.min(
                (int) (config.getInitialReconnectDelayMs() * Math.pow(2, attempt)),
                config.getMaxReconnectDelayMs()
        );
    }
}
