package com.java_template.entity.emailOpenTracker;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class EmailOpenTrackerWorkflow {

    public CompletableFuture<ObjectNode> process_record_open_event(ObjectNode entity) {
        // Example: Mark the event as recorded with timestamp if not present
        if (!entity.has("openTimestamp")) {
            entity.put("openTimestamp", System.currentTimeMillis());
        }
        entity.put("state", "open_event_recorded");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> process_validate_open_event(ObjectNode entity) {
        // Example validation: ensure required fields exist
        if (!entity.has("emailId") || !entity.has("recipientEmail")) {
            entity.put("state", "validation_failed");
            entity.put("error", "Missing required fields");
        } else {
            entity.put("state", "open_event_validated");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> process_enrich_open_event(ObjectNode entity) {
        // Example enrichment: add user agent info or geo-location placeholder
        if (!entity.has("userAgent")) {
            entity.put("userAgent", "unknown");
        }
        entity.put("state", "open_event_enriched");
        return CompletableFuture.completedFuture(entity);
    }
}