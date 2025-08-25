package com.java_template.common.grpc.client;

public interface CalculationExecutionStrategy {
    void run(final Runnable run);
}
