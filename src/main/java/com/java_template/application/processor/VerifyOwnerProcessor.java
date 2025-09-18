package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
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
 * VerifyOwnerProcessor - Completes owner verification process
 * 
 * This processor handles the verification of an owner when all
 * verification requirements are met and documents are validated.
 */
@Component
public class VerifyOwnerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifyOwnerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VerifyOwnerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Verifying Owner for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Owner.class)
                .validate(this::isValidEntityWithMetadata, "Invalid owner entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Owner> entityWithMetadata) {
        Owner entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        return entity != null && entity.isValid() && technicalId != null && "registered".equals(currentState);
    }

    /**
     * Main business logic for verifying an owner
     */
    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {

        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();

        logger.debug("Verifying owner: {} {} with ID: {}", 
                    owner.getFirstName(), owner.getLastName(), currentEntityId);

        // Log verification completion
        logger.info("Owner {} {} (ID: {}) has been verified", 
                   owner.getFirstName(), owner.getLastName(), currentEntityId);

        // Note: Actual verification logic (document validation, background checks)
        // would be implemented here or in external services
        // This processor focuses on the completion of the verification process

        return entityWithMetadata;
    }
}
