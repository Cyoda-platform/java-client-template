package com.java_template.common.grpc.client.connection;

import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.stub.StreamObserver;
import java.util.function.Consumer;

record CloudEventStreamObserver(
        Consumer<CloudEvent> onNext,
        Consumer<Throwable> onError,
        Runnable onComplete
) implements StreamObserver<CloudEvent> {

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
