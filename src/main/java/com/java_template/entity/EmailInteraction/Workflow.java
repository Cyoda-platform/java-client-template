package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> processEmailInteraction(ObjectNode entity) {
        logger.info("processEmailInteraction workflow started");

        return processValidateEventAndEmail(entity)
                .thenCompose(this::processGetCurrentWeek)
                .thenCompose(this::processUpdateInteractionSummary);
    }

    public CompletableFuture<ObjectNode> processValidateEventAndEmail(ObjectNode entity) {
        String eventType = entity.get("eventType").asText(null);
        String email = normalizeEmail(entity.get("email").asText(null));
        if (!( "open".equals(eventType) || "click".equals(eventType)) || email == null) {
            CompletableFuture<ObjectNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Invalid interaction event or email"));
            return failed;
        }
        entity.put("eventType", eventType);
        entity.put("email", email);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processGetCurrentWeek(ObjectNode entity) {
        String currentWeek = getCurrentIsoWeek();
        entity.put("currentWeek", currentWeek);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> processUpdateInteractionSummary(ObjectNode entity) {
        // TODO: Replace below mock logic with real persistence calls
        String eventType = entity.get("eventType").asText();
        String currentWeek = entity.get("currentWeek").asText();

        // Mock interaction summary node stored in entity for prototype
        ObjectNode summaryNode = entity.with("interactionSummary");
        int emailOpens = summaryNode.path("emailOpens").asInt(0);
        int emailClicks = summaryNode.path("emailClicks").asInt(0);

        if ("open".equals(eventType)) {
            emailOpens++;
        } else if ("click".equals(eventType)) {
            emailClicks++;
        }

        summaryNode.put("emailOpens", emailOpens);
        summaryNode.put("emailClicks", emailClicks);

        logger.debug("Updated InteractionSummary for week {}", currentWeek);

        return CompletableFuture.completedFuture(entity);
    }

    private String normalizeEmail(String email) {
        if (email == null) return null;
        String norm = email.trim().toLowerCase();
        if (norm.isEmpty() || !norm.contains("@")) return null;
        return norm;
    }

    private String getCurrentIsoWeek() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ISO_WEEK_DATE)
                .substring(0, 8);
    }
}