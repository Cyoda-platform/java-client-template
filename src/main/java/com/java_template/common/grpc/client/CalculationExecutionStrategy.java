package com.java_template.common.grpc.client;

/**
 * ABOUTME: Strategy interface for executing calculation tasks with different
 * threading models (platform threads vs virtual threads).
 */
public interface CalculationExecutionStrategy {
    void run(final Runnable run);
}
