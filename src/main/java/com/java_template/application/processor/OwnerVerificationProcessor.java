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
 * OwnerVerificationProcessor - Complete owner background verification
 */
@Component
public class OwnerVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OwnerVerificationProcessor(SerializerFactory serializerFactory) {
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
     * Complete owner background verification
     */
    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {

        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Verifying owner: {} in state: {}", owner.getOwnerId(), currentState);

        // Verify owner is in registered state
        if (!"registered".equals(currentState)) {
            logger.warn("Owner {} is not in registered state, current state: {}", owner.getOwnerId(), currentState);
        }

        // Perform background check (simulated)
        boolean backgroundCheckPassed = performBackgroundCheck(owner);
        
        // Verify contact information (simulated)
        boolean contactVerified = verifyContactInformation(owner);

        // Update verification status based on checks
        if (backgroundCheckPassed && contactVerified) {
            owner.setVerificationStatus("Verified");
            logger.info("Owner {} verification completed successfully", owner.getOwnerId());
        } else {
            owner.setVerificationStatus("Failed");
            logger.warn("Owner {} verification failed", owner.getOwnerId());
        }

        // Send verification confirmation (simulated)
        logger.info("Verification confirmation sent to: {}", owner.getEmail());

        return entityWithMetadata;
    }

    /**
     * Simulate background check process
     */
    private boolean performBackgroundCheck(Owner owner) {
        // Simulated background check logic
        // In real implementation, this would integrate with external services
        logger.debug("Performing background check for owner: {}", owner.getOwnerId());
        
        // Simple validation: check if owner has required information
        return owner.getFirstName() != null && 
               owner.getLastName() != null && 
               owner.getEmail() != null &&
               owner.getAddress() != null;
    }

    /**
     * Simulate contact information verification
     */
    private boolean verifyContactInformation(Owner owner) {
        // Simulated contact verification logic
        logger.debug("Verifying contact information for owner: {}", owner.getOwnerId());
        
        // Simple validation: check email format and address completeness
        boolean emailValid = owner.getEmail().contains("@") && owner.getEmail().contains(".");
        boolean addressValid = owner.getAddress() != null && 
                              owner.getAddress().getLine1() != null &&
                              owner.getAddress().getCity() != null;
        
        return emailValid && addressValid;
    }
}
