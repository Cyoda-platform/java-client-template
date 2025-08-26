package com.java_template.application.processor;
import com.java_template.application.entity.coverphoto.version_1.CoverPhoto;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class PublishProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public PublishProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CoverPhoto for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CoverPhoto.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CoverPhoto entity) {
        return entity != null && entity.isValid();
    }

    private CoverPhoto processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CoverPhoto> context) {
        CoverPhoto entity = context.entity();

        // Only attempt publish flow for ingested items
        if (entity.getIngestionStatus() == null) {
            logger.info("CoverPhoto {} has null ingestionStatus, marking as FAILED", entity.getId());
            entity.setIngestionStatus("FAILED");
            return entity;
        }

        String currentStatus = entity.getIngestionStatus().trim().toUpperCase();
        if (!"INGESTED".equals(currentStatus)) {
            // Nothing to do if not in INGESTED state
            logger.debug("CoverPhoto {} is in status {}, skipping publish logic", entity.getId(), entity.getIngestionStatus());
            return entity;
        }

        try {
            // Build deduplication condition: match by sourceUrl OR title
            SearchConditionRequest condition = SearchConditionRequest.group("OR",
                Condition.of("$.sourceUrl", "EQUALS", entity.getSourceUrl() == null ? "" : entity.getSourceUrl()),
                Condition.of("$.title", "EQUALS", entity.getTitle() == null ? "" : entity.getTitle())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                CoverPhoto.ENTITY_NAME,
                String.valueOf(CoverPhoto.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode items = itemsFuture.join();

            boolean foundDuplicate = false;
            for (JsonNode node : items) {
                if (node == null || node.isNull()) continue;
                JsonNode idNode = node.get("id");
                String existingId = idNode != null && !idNode.isNull() ? idNode.asText() : null;
                // skip self
                if (existingId != null && existingId.equals(entity.getId())) {
                    continue;
                }
                // If any other record matches, treat as duplicate
                foundDuplicate = true;
                break;
            }

            if (foundDuplicate) {
                // Duplicate found: do not publish this entity. Mark as FAILED to indicate it won't be published.
                // (We intentionally avoid modifying other CoverPhoto records here to prevent assumptions about id format.)
                entity.setIngestionStatus("FAILED");
                entity.setUpdatedAt(Instant.now().toString());
                logger.info("CoverPhoto {} detected as duplicate, marking as FAILED", entity.getId());
            } else {
                // No duplicate -> publish
                entity.setIngestionStatus("PUBLISHED");
                // Ensure publishedDate is set
                if (entity.getPublishedDate() == null || entity.getPublishedDate().isBlank()) {
                    entity.setPublishedDate(Instant.now().toString());
                }
                // Ensure viewCount initialized
                if (entity.getViewCount() == null) {
                    entity.setViewCount(0);
                }
                entity.setUpdatedAt(Instant.now().toString());
                logger.info("CoverPhoto {} published successfully", entity.getId());
            }
        } catch (Exception ex) {
            // On unexpected errors, mark as FAILED and log
            logger.error("Error during publish processing for CoverPhoto {}: {}", entity.getId(), ex.getMessage(), ex);
            entity.setIngestionStatus("FAILED");
            entity.setUpdatedAt(Instant.now().toString());
        }

        return entity;
    }
}