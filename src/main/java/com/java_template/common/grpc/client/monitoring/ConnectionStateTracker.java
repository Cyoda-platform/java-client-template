package com.java_template.common.grpc.client.monitoring;

import io.grpc.ConnectivityState;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * ABOUTME: Interface for tracking gRPC connection and observer state changes
 * with callback support for state transition monitoring.
 */
public interface ConnectionStateTracker {
    void trackObserverStateChange(ObserverState newState);

    void trackConnectionStateChanged(
            Supplier<ConnectivityState> newStateProvider,
            BiConsumer<ConnectivityState, Runnable> initNextListener
    );
}