package com.java_template.entity.entitymodel;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Nullable;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("entitymodel")
public class Workflow {

    private final Logger logger = LoggerFactory.getLogger(Workflow.class);

    // Condition: check if entity has valid relatedId UUID
    public boolean hasValidRelatedId(ObjectNode entity) {
        if (!entity.hasNonNull("relatedId")) {
            logger.info("Condition hasValidRelatedId: relatedId missing");
            return false;
        }
        try {
            UUID.fromString(entity.get("relatedId").asText());
            logger.info("Condition hasValidRelatedId: valid relatedId found");
            return true;
        } catch (IllegalArgumentException e) {
            logger.info("Condition hasValidRelatedId: invalid relatedId format");
            return false;
        }
    }

    // Condition: negation of hasValidRelatedId
    public boolean notHasValidRelatedId(ObjectNode entity) {
        boolean result = !hasValidRelatedId(entity);
        logger.info("Condition notHasValidRelatedId: {}", result);
        return result;
    }

    // Condition: determine if processing failed by checking entity attribute "processingFailed" or similar
    public boolean processFailed(ObjectNode entity) {
        // Assuming processing failure is marked by a boolean field "processingFailed" in entity
        boolean failed = entity.hasNonNull("processingFailed") && entity.get("processingFailed").asBoolean();
        logger.info("Condition processFailed: {}", failed);
        return failed;
    }

    // Action: async processing of entity before persistence
    public CompletableFuture<ObjectNode> processEntity(ObjectNode entity) {
        logger.info("Action processEntity: starting async processing");

        // Add lastModified timestamp
        entity.put("lastModified", Instant.now().toString());

        if (!hasValidRelatedId(entity)) {
            logger.warn("Action processEntity: relatedId missing or invalid, skipping supplementary fetch");
            return CompletableFuture.completedFuture(entity);
        }

        UUID relatedId;
        try {
            relatedId = UUID.fromString(entity.get("relatedId").asText());
        } catch (IllegalArgumentException e) {
            logger.warn("Action processEntity: invalid relatedId format, skipping supplementary fetch");
            return CompletableFuture.completedFuture(entity);
        }

        // TODO: Replace with actual call to entityService.getItem("supplementaryModel", ENTITY_VERSION, relatedId)
        // For prototype, simulate supplementary data fetch with completed future
        CompletableFuture<ObjectNode> supplementaryFuture = fetchSupplementaryDataMock(relatedId);

        return supplementaryFuture.handle((supplementaryData, ex) -> {
            if (ex != null) {
                logger.error("Action processEntity: failed to fetch supplementary entity: {}", ex.getMessage());
                return entity;
            }
            if (supplementaryData != null) {
                entity.set("supplementaryData", supplementaryData);
            }
            logger.info("Action processEntity: completed async processing");
            return entity;
        });
    }

    // Mock method simulating async fetch of supplementary data
    private CompletableFuture<ObjectNode> fetchSupplementaryDataMock(UUID relatedId) {
        // TODO: replace with real entityService call
        logger.info("Fetching supplementary data for relatedId {}", relatedId);
        return CompletableFuture.completedFuture(null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class IdResponse {
        private String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ErrorResponse {
        private String error;
        private String message;
    }

}