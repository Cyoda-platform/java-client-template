package com.java_template.application.processor;

import com.java_template.application.entity.hnitem.version_1.HnItem;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * ValidateHnItemProcessor - Validates HN item data structure, required fields, and business rules
 * 
 * This processor validates:
 * - Required fields (id, type)
 * - Valid type values
 * - Type-specific business rules
 * - Sets system timestamps
 */
@Component
public class ValidateHnItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateHnItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateHnItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating HnItem for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(HnItem.class)
                .validate(this::isValidEntityWithMetadata, "Invalid HnItem entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HnItem> entityWithMetadata) {
        HnItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    /**
     * Main validation logic processing method
     */
    private EntityWithMetadata<HnItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HnItem> context) {

        EntityWithMetadata<HnItem> entityWithMetadata = context.entityResponse();
        HnItem entity = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Validating entity: {} in state: {}", entity.getId(), currentState);

        // Validate required fields
        if (entity.getId() == null) {
            throw new IllegalArgumentException("HN item ID is required");
        }

        if (entity.getType() == null || !isValidType(entity.getType())) {
            throw new IllegalArgumentException("Invalid or missing HN item type. Must be one of: job, story, comment, poll, pollopt");
        }

        // Validate type-specific rules
        validateTypeSpecificRules(entity);

        // Set system timestamps
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }

        logger.info("HnItem {} validated successfully", entity.getId());

        return entityWithMetadata;
    }

    /**
     * Check if the type is valid
     */
    private boolean isValidType(String type) {
        return "job".equals(type) || "story".equals(type) || "comment".equals(type) || 
               "poll".equals(type) || "pollopt".equals(type);
    }

    /**
     * Validate type-specific business rules
     */
    private void validateTypeSpecificRules(HnItem entity) {
        String type = entity.getType();
        
        if ("comment".equals(type) && entity.getParent() == null) {
            throw new IllegalArgumentException("Comment must have parent");
        }
        
        if ("pollopt".equals(type) && entity.getPoll() == null) {
            throw new IllegalArgumentException("Poll option must reference poll");
        }
        
        if ("poll".equals(type) && (entity.getParts() == null || entity.getParts().isEmpty())) {
            throw new IllegalArgumentException("Poll must have parts");
        }
    }
}
