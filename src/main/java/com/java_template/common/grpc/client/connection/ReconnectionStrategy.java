package com.java_template.common.grpc.client.connection;

public interface ReconnectionStrategy {
    void reset();
    void requestReconnection(Runnable reconnect);
}

