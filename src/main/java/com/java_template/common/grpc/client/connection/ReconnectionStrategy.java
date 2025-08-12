package com.java_template.common.grpc.client.connection;

import java.util.concurrent.Callable;

public interface ReconnectionStrategy {
    void requestReconnection(Callable<Void> reconnect);
}

