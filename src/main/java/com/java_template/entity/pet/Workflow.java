package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Component("pet")
public class Workflow {

    public CompletableFuture<ObjectNode> startProcessing(ObjectNode entity) {
        return normalizeStatus(entity);
    }

    public CompletableFuture<ObjectNode> continueProcessing(ObjectNode entity) {
        return additionalAsyncSideEffects(entity);
    }

    public CompletableFuture<ObjectNode> finish(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> normalizeStatus(ObjectNode entity) {
        if (entity.has("status") && entity.get("status").isTextual()) {
            entity.put("status", entity.get("status").asText().toLowerCase(Locale.ROOT));
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> additionalAsyncSideEffects(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity);
    }
}