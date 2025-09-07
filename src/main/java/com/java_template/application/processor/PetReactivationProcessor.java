package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PetReactivationProcessor - Handles pet reactivation business logic
 * 
 * Reactivates an inactive pet, including:
 * - Validating pet is in INACTIVE state
 * - Verifying owner is still active
 * - Making pet eligible for service orders again
 */
@Component
public class PetReactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetReactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetReactivationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet reactivation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Pet.class)
            .validate(this::isValidEntityWithMetadata, "Invalid pet reactivation data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet pet = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();
        return pet != null && pet.isValid() && "INACTIVE".equals(currentState);
    }

    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {
        
        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing pet reactivation for: {} in state: {}", pet.getPetId(), currentState);

        // 1. Verify owner is still active
        verifyOwnerIsActive(pet.getOwnerId());

        // 2. Pet becomes eligible for service orders again
        logger.info("Pet {} reactivated successfully", pet.getPetId());

        return entityWithMetadata;
    }

    private void verifyOwnerIsActive(String ownerId) {
        try {
            ModelSpec ownerModelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> ownerResponse = entityService.findByBusinessId(
                ownerModelSpec, ownerId, "ownerId", Owner.class);
            
            if (ownerResponse == null) {
                throw new IllegalArgumentException("Owner not found: " + ownerId);
            }
            
            String ownerState = ownerResponse.getState();
            if (!"ACTIVE".equals(ownerState)) {
                throw new IllegalArgumentException("Owner is not active: " + ownerId + " (state: " + ownerState + ")");
            }
            
            logger.debug("Owner {} verified as active for pet reactivation", ownerId);
        } catch (Exception e) {
            logger.error("Failed to verify owner during pet reactivation: {}", ownerId, e);
            throw new RuntimeException("Owner verification failed: " + e.getMessage(), e);
        }
    }
}
