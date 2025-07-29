package com.java_template.common.grpc.client_v2;

interface ObserverStateTracker {
    void trackObserverStateChange(ObserverState newState);
}