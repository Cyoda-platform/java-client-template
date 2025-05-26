package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class Workflow {

    public CompletableFuture<ObjectNode> processComment(ObjectNode comment) {
        // orchestrate workflow steps without business logic here
        return processAddTimestamp(comment);
    }

    private CompletableFuture<ObjectNode> processAddTimestamp(ObjectNode comment) {
        return CompletableFuture.supplyAsync(() -> {
            if (!comment.has("timestamp") || comment.path("timestamp").isNull()) {
                comment.put("timestamp", Instant.now().toString());
            }
            return comment;
        });
    }
}