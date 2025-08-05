package com.java_template.common.grpc.client_v2;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.java_template.common.config.GrpcClientAutoConfiguration.EXTERNAL_CALCULATIONS_THREAD_POOL;

public class VirtualThreadsCalculationExecutor implements CalculationExecutionStrategy {
    private final ExecutorService calculationExecutionService = Executors.newFixedThreadPool(
            EXTERNAL_CALCULATIONS_THREAD_POOL,
            Thread.ofVirtual().name("external-calculation").factory()
    );

    @Override
    public void run(final Runnable run) {
        calculationExecutionService.submit(run);
    }
}
