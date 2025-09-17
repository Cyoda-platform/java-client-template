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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ValidateHNItemProcessor - Validates HN item structure and required fields
 */
@Component
public class ValidateHNItemProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateHNItemProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateHNItemProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Validating HNItem for request: {}", request.getId());

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

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<HNItem> entityWithMetadata) {
        HNItem entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    /**
     * Main validation logic processing method
     */
    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Validating HNItem: {} of type: {}", entity.getId(), entity.getType());

        // Validate required fields
        if (entity.getId() == null) {
            throw new IllegalArgumentException("ID is required for HNItem");
        }

        if (entity.getType() == null || entity.getType().trim().isEmpty()) {
            throw new IllegalArgumentException("Type is required for HNItem");
        }

        // Validate type is one of the allowed values
        String type = entity.getType().toLowerCase();
        if (!type.equals("story") && !type.equals("comment") && !type.equals("job") && 
            !type.equals("poll") && !type.equals("pollopt")) {
            throw new IllegalArgumentException("Invalid item type: " + entity.getType() + 
                ". Must be one of: story, comment, job, poll, pollopt");
        }

        // Validate story-specific requirements
        if ("story".equals(type) && (entity.getTitle() == null || entity.getTitle().trim().isEmpty())) {
            throw new IllegalArgumentException("Story title is required for story type items");
        }

        logger.info("HNItem {} validated successfully", entity.getId());
        return entityWithMetadata;
    }
}
