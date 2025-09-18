package com.java_template.application.processor;

import com.java_template.application.entity.adoption.version_1.Adoption;
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
 * CancelAdoptionProcessor - Cancels adoption application
 * 
 * This processor handles the cancellation of an adoption application,
 * logging the cancellation activity for tracking purposes.
 */
@Component
public class CancelAdoptionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CancelAdoptionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CancelAdoptionProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Cancelling Adoption application for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Adoption.class)
                .validate(this::isValidEntityWithMetadata, "Invalid adoption entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Adoption> entityWithMetadata) {
        Adoption entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        return entity != null && entity.isValid() && technicalId != null && "pending".equals(currentState);
    }

    /**
     * Main business logic for cancelling an adoption application
     */
    private EntityWithMetadata<Adoption> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Adoption> context) {

        EntityWithMetadata<Adoption> entityWithMetadata = context.entityResponse();
        Adoption adoption = entityWithMetadata.entity();
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();

        logger.debug("Cancelling adoption application for pet: {} and owner: {} with ID: {}", 
                    adoption.getPetId(), adoption.getOwnerId(), currentEntityId);

        // Log cancellation activity
        logger.info("Adoption application (ID: {}) for pet {} and owner {} has been cancelled", 
                   currentEntityId, adoption.getPetId(), adoption.getOwnerId());

        // Note: Cancellation details (reason, date) would be tracked in metadata or separate fields
        // This processor focuses on the adoption-specific aspects of the cancellation

        return entityWithMetadata;
    }
}
