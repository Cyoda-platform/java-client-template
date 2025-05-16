package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Workflow {

    // Orchestrates the workflow steps without business logic
    public CompletableFuture<ObjectNode> processOrder(ObjectNode entity) {
        return processSetCreatedAt(entity)
                .thenCompose(this::processValidateSide)
                .thenCompose(this::processSetDefaultStatus)
                .thenCompose(this::processValidateAmount)
                .thenCompose(this::processExecution);
    }

    private CompletableFuture<ObjectNode> processSetCreatedAt(ObjectNode entity) {
        if (!entity.has("createdAt")) {
            entity.put("createdAt", Instant.now().toString());
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processValidateSide(ObjectNode entity) {
        if (entity.has("side")) {
            String sideStr = entity.get("side").asText().toLowerCase(Locale.ROOT);
            if (!sideStr.equals("buy") && !sideStr.equals("sell")) {
                entity.put("status", "rejected");
                entity.put("rejectionReason", "Invalid side value");
                log.warn("Order rejected due to invalid side: {}", sideStr);
                return CompletableFuture.completedFuture(entity);
            }
            entity.put("side", sideStr);
            return CompletableFuture.completedFuture(entity);
        } else {
            entity.put("status", "rejected");
            entity.put("rejectionReason", "Missing side");
            log.warn("Order rejected due to missing side");
            return CompletableFuture.completedFuture(entity);
        }
    }

    private CompletableFuture<ObjectNode> processSetDefaultStatus(ObjectNode entity) {
        if (!entity.has("status")) {
            entity.put("status", "pending");
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processValidateAmount(ObjectNode entity) {
        double amount = entity.has("amount") ? entity.get("amount").asDouble() : 0.0;

        if (amount < 0) {
            entity.put("status", "rejected");
            entity.put("rejectionReason", "Negative amount not allowed");
            log.warn("Order rejected due to negative amount: {}", amount);
            return CompletableFuture.completedFuture(entity);
        }
        if (amount >= 10_000) {
            entity.put("status", "rejected");
            entity.put("rejectionReason", "Amount exceeds limit");
            log.info("Order rejected due to amount >= 10,000: {}", amount);
            return CompletableFuture.completedFuture(entity);
        }
        if (amount >= 5_000 && amount < 10_000) {
            entity.put("status", "review");
            log.info("Order marked for review due to amount: {}", amount);
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processExecution(ObjectNode entity) {
        String status = entity.get("status").asText();
        if ("pending".equals(status) || "review".equals(status)) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    log.info("Simulating async execution workflow, sleeping 100ms");
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted during execution simulation", e);
                }
                entity.put("status", "executed");
                // Note: executedAmountsByPair map update should be handled outside or via event-driven mechanism
                log.info("Order status updated to executed");
                return entity;
            });
        }
        return CompletableFuture.completedFuture(entity);
    }
}