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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VerifyOwnerProcessor - Verifies owner's contact information
 * 
 * Purpose: Verify owner's contact information and address
 * Transition: registered -> verified (manual)
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
        logger.info("Processing Owner verification for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     * Simulates verification process for owner's contact information
     */
    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {

        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner entity = entityWithMetadata.entity();

        logger.debug("Verifying owner: {}", entity.getOwnerId());

        // Simulate verification process
        // In a real system, this would:
        // - Send verification email to owner.email
        // - Verify address through postal service or third-party service
        // - Check phone number validity through SMS or call
        
        // For now, we'll just log the verification process
        logger.info("Simulating email verification for owner {} at {}", 
                   entity.getOwnerId(), entity.getEmail());
        
        logger.info("Simulating address verification for owner {} at {}, {}, {} {}", 
                   entity.getOwnerId(), 
                   entity.getAddress().getStreet(),
                   entity.getAddress().getCity(),
                   entity.getAddress().getState(),
                   entity.getAddress().getZipCode());
        
        logger.info("Simulating phone verification for owner {} at {}", 
                   entity.getOwnerId(), entity.getPhone());

        logger.info("Owner {} verified successfully", entity.getOwnerId());

        return entityWithMetadata;
    }
}
