package com.java_template.common.grpc.client.monitoring;

/**
 * ABOUTME: Enumeration defining the various states of gRPC stream observer
 * during connection lifecycle from disconnected to ready state.
 */
public enum ObserverState {

    DISCONNECTED(false),
    ERROR(false),
    CONNECTING(true),
    JOINING(true),
    AWAITS_GREET(true),
    READY(true),
    IDLE(false);  // System has given up reconnecting, awaiting resurrection

    public final boolean operational;

    ObserverState(boolean operational) {
        this.operational = operational;
    }
}
