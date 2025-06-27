package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import static com.java_template.common.config.Config.*;

@Component("subscriber")
public class Workflow {

    public CompletableFuture<ObjectNode> initializeSubscription(ObjectNode entity) {
        if (!entity.has("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
        }
        entity.put("entityVersion", ENTITY_VERSION);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> subscribe(ObjectNode entity) {
        return initializeSubscription(entity);
    }

    public CompletableFuture<ObjectNode> none(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> subscribed(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity);
    }
}