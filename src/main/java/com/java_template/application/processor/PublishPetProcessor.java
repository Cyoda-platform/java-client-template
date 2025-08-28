package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class PublishPetProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PublishPetProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PublishPetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }
    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        if (entity == null) {
            logger.warn("PublishPetProcessor received null entity context");
            return null;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus != null) {
            String s = currentStatus.trim().toLowerCase();
            // If already in terminal or non-publishable states, do nothing
            if (s.equals("adopted") || s.equals("archived")) {
                logger.info("Pet {} is in terminal state '{}', skipping publish", entity.getPetId(), currentStatus);
                return entity;
            }
            // If already available, nothing to do
            if (s.equals("available")) {
                logger.info("Pet {} already available, skipping publish", entity.getPetId());
                return entity;
            }
        }

        // Basic readiness checks for publishing
        boolean hasImages = false;
        List<String> images = entity.getImages();
        if (images != null && !images.isEmpty()) hasImages = true;

        boolean healthy = true;
        List<String> healthRecords = entity.getHealthRecords();
        if (healthRecords == null || healthRecords.isEmpty()) {
            healthy = false;
        } else {
            // If any health record indicates a critical condition, mark unhealthy
            for (String rec : healthRecords) {
                if (rec == null) continue;
                String low = rec.toLowerCase();
                if (low.contains("sick") || low.contains("critical") || low.contains("untreatable") || low.contains("needs surgery")) {
                    healthy = false;
                    break;
                }
            }
        }

        Map<String, Object> metadata = entity.getMetadata();
        if (metadata == null) {
            // should not happen because isValid enforces non-null metadata, but guard anyway
            // can't call setter for metadata map directly if null -> create one
            // However Pet.getMetadata() returns a map initialized in POJO so this branch likely unused
        }

        // Ensure createdAt exists
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
            entity.setCreatedAt(Instant.now().toString());
            if (entity.getMetadata() != null) {
                entity.getMetadata().put("createdAtSetBy", "PublishPetProcessor");
            }
        }

        if (hasImages && healthy) {
            entity.setStatus("Available");
            if (entity.getMetadata() != null) {
                entity.getMetadata().put("publishedBy", "PublishPetProcessor");
                entity.getMetadata().put("publishedAt", Instant.now().toString());
            }
            logger.info("Pet {} published as Available", entity.getPetId());
        } else {
            // Do not publish; set a clear non-available status to drive further processors/human review
            if (!hasImages) {
                entity.setStatus("ENRICHMENT_REQUIRED");
                if (entity.getMetadata() != null) {
                    entity.getMetadata().put("publishReason", "missing_images");
                }
                logger.info("Pet {} not published: missing images", entity.getPetId());
            } else {
                entity.setStatus("HEALTH_CHECK_REQUIRED");
                if (entity.getMetadata() != null) {
                    entity.getMetadata().put("publishReason", "health_issues");
                }
                logger.info("Pet {} not published: health checks required", entity.getPetId());
            }
        }

        return entity;
    }
}