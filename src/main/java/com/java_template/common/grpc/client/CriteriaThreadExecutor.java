package com.java_template.common.grpc.client;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.CRITERIA_THREAD_POOL;

/**
 * ABOUTME: Executor for criteria calculation events using a dedicated thread pool.
 * Criteria events are medium-weight operations that evaluate workflow conditions
 * and are generally faster than processor events.
 */
public class CriteriaThreadExecutor implements CalculationExecutionStrategy {
    private static final Logger log = LoggerFactory.getLogger(CriteriaThreadExecutor.class);

    private final ExecutorService executorService;
    private final boolean useVirtualThreads;

    public CriteriaThreadExecutor(boolean useVirtualThreads) {
        this.useVirtualThreads = useVirtualThreads;
        if (useVirtualThreads) {
            this.executorService = Executors.newFixedThreadPool(
                CRITERIA_THREAD_POOL,
                Thread.ofVirtual().name("criteria-calculation-", 0).factory()
            );
            log.info("Initialized CriteriaThreadExecutor with {} virtual threads", CRITERIA_THREAD_POOL);
        } else {
            this.executorService = Executors.newFixedThreadPool(
                CRITERIA_THREAD_POOL,
                Thread.ofPlatform().name("criteria-calculation-", 0).factory()
            );
            log.info("Initialized CriteriaThreadExecutor with {} platform threads", CRITERIA_THREAD_POOL);
        }
    }

    @Override
    public void run(final Runnable task) {
        executorService.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down CriteriaThreadExecutor...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("CriteriaThreadExecutor did not terminate in time, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while shutting down CriteriaThreadExecutor", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("CriteriaThreadExecutor shutdown complete");
    }
}

