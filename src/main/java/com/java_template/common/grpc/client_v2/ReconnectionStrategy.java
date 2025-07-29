package com.java_template.common.grpc.client_v2;

import java.util.concurrent.Callable;

public interface ReconnectionStrategy {
    void requestReconnection(Callable<Void> reconnect);
}

