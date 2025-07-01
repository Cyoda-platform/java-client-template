package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("pet")
public class Workflow {

    public CompletableFuture<ObjectNode> processpet(ObjectNode entity) {
        return normalizeStatus(entity)
                .thenCompose(this::additionalAsyncSideEffects);
    }

    private CompletableFuture<ObjectNode> normalizeStatus(ObjectNode entity) {
        if (entity.has("status") && entity.get("status").isTextual()) {
            entity.put("status", entity.get("status").asText().toLowerCase(Locale.ROOT));
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> additionalAsyncSideEffects(ObjectNode entity) {
        // TODO: add any asynchronous side effects or normalization here if needed
        return CompletableFuture.completedFuture(entity);
    }
}