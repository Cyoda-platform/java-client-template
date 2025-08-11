package com.java_template.common.grpc.client_v2;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.java_template.common.config.Config.FAILED_RECONNECTS_LIMIT;
import static com.java_template.common.config.Config.INITIAL_RECONNECT_DELAY_MS;
import static com.java_template.common.config.Config.MAX_RECONNECT_DELAY_MS;

public class DefaultReconnectionStrategy implements ReconnectionStrategy {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final ScheduledExecutorService reconnectionScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean isReconnectScheduled = new AtomicBoolean(false);
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
    public void requestReconnection(final Callable<Void> reconnect) {
        log.info("Reconnect requested");
        if (!isReconnectScheduled.compareAndSet(false, true)) {
            log.warn("Reconnect is already scheduled. Request will be ignored");
            return;
        }

        scheduleReconnectWithBackoff(reconnect);
    }

    private void scheduleReconnectWithBackoff(final Callable<Void> reconnect) {
        final int attempt = failedReconnectsCount.get();
        final long nextDelayMs = Math.min(
                (int) (INITIAL_RECONNECT_DELAY_MS * Math.pow(2, attempt)),
                MAX_RECONNECT_DELAY_MS
        );

        log.info("Scheduling reconnect attempt #{} with a {}ms delay.", attempt + 1, nextDelayMs);

        final Runnable handleReconnect = () -> {
            try {
                log.info("Attempt to reconnect...");
                reconnect.call();
                log.info("Successful reconnected");

                failedReconnectsCount.set(0);
                isReconnectScheduled.set(false);
            } catch (Exception e) {
                final var failsCount = failedReconnectsCount.incrementAndGet();
                log.warn("Reconnect failed. Attempt #{}", failsCount, e);

                if (failsCount < FAILED_RECONNECTS_LIMIT) {
                    scheduleReconnectWithBackoff(reconnect);
                } else {
                    log.error(
                            "Failed reconnects ({}) reached the limit of {}. Giving up!!!",
                            failsCount,
                            FAILED_RECONNECTS_LIMIT
                    );
                    failedReconnectsCount.set(0);
                    isReconnectScheduled.set(false);
                }
            }
        };

        reconnectionScheduler.schedule(handleReconnect, nextDelayMs, TimeUnit.MILLISECONDS);
    }
}
