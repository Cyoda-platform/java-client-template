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

import java.time.LocalDateTime;

/**
 * ValidateHNItemProcessor - Validates HNItem data integrity and business rules
 * 
 * This processor handles the validate_item transition from created to validated.
 * It performs validation checks and updates the entity timestamp.
 * The actual validation logic is handled by the HNItemValidCriterion.
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
        logger.info("Processing ValidateHNItem for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Validate HNItem data integrity and business rules
     * The actual validation is handled by the criterion, this processor just updates timestamp
     */
    private EntityWithMetadata<HNItem> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<HNItem> context) {

        EntityWithMetadata<HNItem> entityWithMetadata = context.entityResponse();
        HNItem entity = entityWithMetadata.entity();

        logger.debug("Validating HNItem: {}", entity.getId());

        // Update timestamp to mark validation processing
        entity.setUpdatedAt(LocalDateTime.now());

        // The actual validation logic is handled by HNItemValidCriterion
        // This processor just updates the timestamp to mark that validation was processed

        logger.info("HNItem {} validation processed successfully", entity.getId());

        return entityWithMetadata;
    }
}
