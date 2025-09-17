package com.java_template.common.grpc.client.connection;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.java_template.common.config.Config.FAILED_RECONNECTS_LIMIT;
import static com.java_template.common.config.Config.INITIAL_RECONNECT_DELAY_MS;
import static com.java_template.common.config.Config.MAX_RECONNECT_DELAY_MS;

/**
 * ABOUTME: Default implementation of ReconnectionStrategy with exponential backoff,
 * retry limits, and scheduled reconnection attempts for gRPC connections.
 */
public class DefaultReconnectionStrategy implements ReconnectionStrategy {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger failedReconnectsCount = new AtomicInteger(0);

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
    }

    @Override
    public void requestReconnection(final Runnable reconnect) {
        log.info("Reconnect requested");

        final int attemptsCount = failedReconnectsCount.getAndIncrement();
        if (attemptsCount > FAILED_RECONNECTS_LIMIT) {
            log.error("Failed reconnects limit ({}) reached. Giving up!!!", FAILED_RECONNECTS_LIMIT);
            return;
        }

        final long reconnectDelayMs = calculateBackoff(attemptsCount);
        log.info("Scheduling reconnect attempt #{} with a {}ms delay.", attemptsCount, reconnectDelayMs);
        reconnectionScheduler.schedule(reconnect, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }

    private long calculateBackoff(final int attempt) {
        return Math.min(
                (int) (INITIAL_RECONNECT_DELAY_MS * Math.pow(2, attempt)),
                MAX_RECONNECT_DELAY_MS
        );
    }
}
