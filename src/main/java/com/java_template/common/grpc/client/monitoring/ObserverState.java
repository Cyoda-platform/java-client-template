package com.java_template.common.grpc.client.monitoring;

public enum ObserverState {
    DISCONNECTED,
    CONNECTING,
    JOINING,
    CONNECTED,
    AWAITS_GREET,
    READY,
}
