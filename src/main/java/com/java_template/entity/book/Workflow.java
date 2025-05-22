package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Workflow {
    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> processBook(ObjectNode bookEntity) {
        return processTrimTitle(bookEntity)
                .thenCompose(this::processPageCount)
                .thenCompose(this::processAddProcessedAt)
                .thenCompose(this::processAsyncAuditLog)
                .exceptionally(ex -> {
                    logger.error("Error in processBook workflow: {}", ex.getMessage(), ex);
                    return bookEntity; // Return original entity on error
                });
    }

    private CompletableFuture<ObjectNode> processTrimTitle(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.hasNonNull("title")) {
                String title = entity.get("title").asText();
                entity.put("title", title.trim());
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processPageCount(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.has("pageCount")) {
                int pageCount = entity.get("pageCount").asInt(-1);
                if (pageCount < 0) {
                    entity.put("pageCount", 0);
                }
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAddProcessedAt(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("processedAt", Instant.now().toString());
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAsyncAuditLog(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            sendAsyncAuditLog(entity);
            return entity;
        });
    }

    private void sendAsyncAuditLog(ObjectNode entity) {
        // TODO: Implement async audit log sending logic here
    }
}