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

/**
 * CreateHnItemProcessor - Handles initial creation and basic validation of HN items
 * 
 * This processor is triggered by the auto_create transition from initial_state to created.
 * It performs:
 * - Basic validation of required fields (id, type)
 * - Data normalization and cleanup
 * - Setting creation metadata
 */
@Component
public class CreateHnItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateHnItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CreateHnItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HnItem creation for request: {}", request.getId());

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
     * Main business logic for creating and validating HN items
     */
    private EntityWithMetadata<HnItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HnItem> context) {

        EntityWithMetadata<HnItem> entityWithMetadata = context.entityResponse();
        HnItem entity = entityWithMetadata.entity();

        logger.debug("Processing HnItem creation: ID={}, Type={}", entity.getId(), entity.getType());

        // Perform basic validation and normalization
        validateRequiredFields(entity);
        normalizeData(entity);

        logger.info("HnItem {} created successfully with type: {}", entity.getId(), entity.getType());

        return entityWithMetadata;
    }

    /**
     * Validates required fields for HN item creation
     */
    private void validateRequiredFields(HnItem entity) {
        if (entity.getId() == null || entity.getId() <= 0) {
            throw new IllegalArgumentException("HnItem ID must be positive");
        }

        if (entity.getType() == null || entity.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("HnItem type is required");
        }

        // Validate type is one of allowed values
        String type = entity.getType().toLowerCase().trim();
        if (!isValidType(type)) {
            throw new IllegalArgumentException("Invalid HnItem type: " + entity.getType());
        }
    }

    /**
     * Normalizes and cleans up HN item data
     */
    private void normalizeData(HnItem entity) {
        // Normalize type to lowercase
        if (entity.getType() != null) {
            entity.setType(entity.getType().toLowerCase().trim());
        }

        // Trim text fields
        if (entity.getBy() != null) {
            entity.setBy(entity.getBy().trim());
        }
        
        if (entity.getTitle() != null) {
            entity.setTitle(entity.getTitle().trim());
        }
        
        if (entity.getUrl() != null) {
            entity.setUrl(entity.getUrl().trim());
        }

        // Set default values for boolean fields if null
        if (entity.getDeleted() == null) {
            entity.setDeleted(false);
        }
        
        if (entity.getDead() == null) {
            entity.setDead(false);
        }
    }

    /**
     * Validates if the type is one of the allowed HN item types
     */
    private boolean isValidType(String type) {
        return "story".equals(type) || 
               "comment".equals(type) || 
               "job".equals(type) || 
               "poll".equals(type) || 
               "pollopt".equals(type);
    }
}
