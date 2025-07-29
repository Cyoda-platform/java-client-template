package com.java_template.common.grpc.client_v2;

import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.stub.StreamObserver;
import java.util.function.Consumer;

class CloudEventStreamObserver implements StreamObserver<CloudEvent> {

    private final Consumer<CloudEvent> onNext;
    private final Consumer<Throwable> onError;
    private final Runnable onComplete;

    CloudEventStreamObserver(
            final Consumer<CloudEvent> onNext,
            final Consumer<Throwable> onError,
            final Runnable onComplete) {
        this.onNext = onNext;
        this.onError = onError;
        this.onComplete = onComplete;
    }

    @Override
    public void onNext(final CloudEvent value) {
        onNext.accept(value);
    }

    @Override
    public void onError(final Throwable t) {
        onError.accept(t);
    }

    @Override
    public void onCompleted() {
        onComplete.run();
    }
}
