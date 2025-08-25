package com.java_template.common.grpc.client.monitoring;

public enum ObserverState {
    DISCONNECTED,
    ERROR,
    CONNECTING,
    JOINING,
    AWAITS_GREET,
    READY,
}
