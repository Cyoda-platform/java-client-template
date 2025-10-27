package com.java_template.common.grpc.client;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.PROCESSOR_THREAD_POOL;

/**
 * ABOUTME: Executor for processor calculation events using a dedicated thread pool.
 * Processor events are heavy, long-running operations that execute business logic
 * and may make multiple gRPC calls with retry logic.
 */
public class ProcessorThreadExecutor implements CalculationExecutionStrategy {
    private static final Logger log = LoggerFactory.getLogger(ProcessorThreadExecutor.class);

    private final ExecutorService executorService;
    private final boolean useVirtualThreads;

    public ProcessorThreadExecutor(boolean useVirtualThreads) {
        this.useVirtualThreads = useVirtualThreads;
        if (useVirtualThreads) {
            this.executorService = Executors.newFixedThreadPool(
                PROCESSOR_THREAD_POOL,
                Thread.ofVirtual().name("processor-calculation-", 0).factory()
            );
            log.info("Initialized ProcessorThreadExecutor with {} virtual threads", PROCESSOR_THREAD_POOL);
        } else {
            this.executorService = Executors.newFixedThreadPool(
                PROCESSOR_THREAD_POOL,
                Thread.ofPlatform().name("processor-calculation-", 0).factory()
            );
            log.info("Initialized ProcessorThreadExecutor with {} platform threads", PROCESSOR_THREAD_POOL);
        }
    }

    @Override
    public void run(final Runnable task) {
        executorService.submit(task);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down ProcessorThreadExecutor...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("ProcessorThreadExecutor did not terminate in time, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while shutting down ProcessorThreadExecutor", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("ProcessorThreadExecutor shutdown complete");
    }
}

