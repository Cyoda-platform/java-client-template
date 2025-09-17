package com.java_template.common.grpc.client.monitoring;

/**
 * ABOUTME: Enumeration defining the various states of gRPC stream observer
 * during connection lifecycle from disconnected to ready state.
 */
public enum ObserverState {
    DISCONNECTED,
    ERROR,
    CONNECTING,
    JOINING,
    AWAITS_GREET,
    READY,
}
