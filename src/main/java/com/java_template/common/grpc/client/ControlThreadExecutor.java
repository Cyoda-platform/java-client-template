package com.java_template.common.grpc.client;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ABOUTME: Executor for control and lightweight events using a dedicated thread pool.
 * Control events include keep-alive, ACK, and greet events that must respond quickly
 * (typically <1000ms) and should not be blocked by heavy processor operations.
 */
public class ControlThreadExecutor implements CalculationExecutionStrategy {
    private static final Logger log = LoggerFactory.getLogger(ControlThreadExecutor.class);

    private final ExecutorService executorService;
    private final boolean useVirtualThreads;
    private final int threadPoolSize;

    public ControlThreadExecutor(boolean useVirtualThreads, int threadPoolSize) {
        this.useVirtualThreads = useVirtualThreads;
        this.threadPoolSize = threadPoolSize;
        if (useVirtualThreads) {
            this.executorService = Executors.newFixedThreadPool(
                threadPoolSize,
                Thread.ofVirtual().name("control-event-", 0).factory()
            );
            log.info("Initialized ControlThreadExecutor with {} virtual threads", threadPoolSize);
        } else {
            this.executorService = Executors.newFixedThreadPool(
                threadPoolSize,
                Thread.ofPlatform().name("control-event-", 0).factory()
            );
            log.info("Initialized ControlThreadExecutor with {} platform threads", threadPoolSize);
        }
    }

    @Override
    public void run(final Runnable task) {
        executorService.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ControlThreadExecutor...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("ControlThreadExecutor did not terminate in time, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while shutting down ControlThreadExecutor", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("ControlThreadExecutor shutdown complete");
    }
}

