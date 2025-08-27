package com.java_template.common.grpc.client.monitoring;

import io.grpc.ConnectivityState;

public interface GrpcConnectionStateProvider {
    ConnectivityState getLastKnownState();
}
