package com.java_template.common.grpc.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.java_template.common.config.Config.EXTERNAL_CALCULATIONS_THREAD_POOL;

public class PlatformThreadsCalculationExecutor implements CalculationExecutionStrategy {
    private final ExecutorService calculationExecutionService = Executors.newFixedThreadPool(
            EXTERNAL_CALCULATIONS_THREAD_POOL,
            Thread.ofPlatform().name("external-calculation").factory()
    );

    @Override
    public void run(final Runnable run) {
        calculationExecutionService.submit(run);
    }
}
