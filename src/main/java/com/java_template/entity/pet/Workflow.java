package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("pet")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    // Condition function: check if "status" field exists and is not null
    public boolean isStatusPresent(ObjectNode entity) {
        try {
            return entity.has("status") && !entity.get("status").isNull();
        } catch (Exception e) {
            logger.error("Error checking status presence", e);
            return false;
        }
    }

    // Condition function: check if status equals "adopted" (case insensitive)
    public boolean isAdoptedStatus(ObjectNode entity) {
        try {
            if (entity.has("status") && !entity.get("status").isNull()) {
                String status = entity.get("status").asText();
                return "adopted".equalsIgnoreCase(status);
            }
            return false;
        } catch (Exception e) {
            logger.error("Error checking adopted status", e);
            return false;
        }
    }

    // Action function: normalize status to lowercase
    public CompletableFuture<ObjectNode> normalizeStatus(ObjectNode entity) {
        try {
            if (entity.has("status") && !entity.get("status").isNull()) {
                String status = entity.get("status").asText();
                entity.put("status", status.toLowerCase());
            }
            return CompletableFuture.completedFuture(entity);
        } catch (Exception e) {
            logger.error("Error normalizing status", e);
            CompletableFuture<ObjectNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    // Action function: trigger adoption side effects asynchronously
    public CompletableFuture<ObjectNode> triggerAdoptionSideEffects(ObjectNode entity) {
        try {
            if (entity.has("status") && "adopted".equalsIgnoreCase(entity.get("status").asText(null))) {
                String technicalId = entity.has("technicalId") ? entity.get("technicalId").asText() : "unknown";
                CompletableFuture.runAsync(() -> {
                    logger.info("Adoption workflow triggered for pet with technicalId={}", technicalId);
                    // TODO: Add notification, audit log, event publishing etc.
                });
            }
            return CompletableFuture.completedFuture(entity);
        } catch (Exception e) {
            logger.error("Error triggering adoption side effects", e);
            CompletableFuture<ObjectNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
}