package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * HNItemValidationProcessor - Validates HN items according to Firebase HN API format
 * 
 * Validates structure and required fields of HN items and performs type-specific validations.
 */
@Component
public class HNItemValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(HNItemValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public HNItemValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HNItem validation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HNItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HNItem entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItem> entityWithMetadata) {
        HNItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Validating HNItem: {}", entity.getId());

        // Validate required fields
        if (entity.getId() == null || entity.getId() <= 0) {
            throw new IllegalArgumentException("ID is required and must be positive");
        }

        if (entity.getType() == null || !isValidType(entity.getType())) {
            throw new IllegalArgumentException("Type must be one of: job, story, comment, poll, pollopt");
        }

        // Type-specific validations
        if ("comment".equals(entity.getType())) {
            if (entity.getParent() == null) {
                throw new IllegalArgumentException("Comments must have a parent");
            }
        }

        if ("pollopt".equals(entity.getType())) {
            if (entity.getPoll() == null) {
                throw new IllegalArgumentException("Poll options must reference a poll");
            }
        }

        if ("poll".equals(entity.getType())) {
            if (entity.getParts() == null || entity.getParts().isEmpty()) {
                throw new IllegalArgumentException("Polls must have poll options");
            }
        }

        // Validate data integrity
        if (entity.getScore() != null && entity.getScore() < 0) {
            throw new IllegalArgumentException("Score cannot be negative");
        }

        if (entity.getDescendants() != null && entity.getDescendants() < 0) {
            throw new IllegalArgumentException("Descendants count cannot be negative");
        }

        // Set validation timestamp
        entity.setValidatedAt(System.currentTimeMillis());

        logger.info("HNItem {} validated successfully", entity.getId());
        return entityWithMetadata;
    }

    private boolean isValidType(String type) {
        return "job".equals(type) || "story".equals(type) || "comment".equals(type) || 
               "poll".equals(type) || "pollopt".equals(type);
    }
}
