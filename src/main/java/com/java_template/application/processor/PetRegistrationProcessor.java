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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * PetRegistrationProcessor - Handles pet registration business logic
 * 
 * Validates and registers a new pet in the system, including:
 * - Validating pet data completeness
 * - Verifying owner exists and is active
 * - Generating unique petId
 * - Setting registration metadata
 * - Updating owner's pet count
 */
@Component
public class PetRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetRegistrationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    
    // Simple counter for generating unique pet IDs
    private static final AtomicLong petIdCounter = new AtomicLong(1);

    public PetRegistrationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet registration for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Pet.class)
            .validate(this::isValidEntityWithMetadata, "Invalid pet registration data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet pet = entityWithMetadata.entity();
        return pet != null && 
               pet.getName() != null && !pet.getName().trim().isEmpty() &&
               pet.getSpecies() != null && !pet.getSpecies().trim().isEmpty() &&
               pet.getOwnerId() != null && !pet.getOwnerId().trim().isEmpty() &&
               pet.getAge() != null && pet.getAge() >= 0 &&
               pet.getWeight() != null && pet.getWeight() > 0;
    }

    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {
        
        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();

        logger.debug("Processing pet registration for: {}", pet.getName());

        // 1. Verify owner exists and is active
        verifyOwnerIsActive(pet.getOwnerId());

        // 2. Generate unique petId
        String petId = generateUniquePetId();
        pet.setPetId(petId);

        // 3. Set registration metadata
        pet.setRegistrationDate(LocalDateTime.now());

        // 4. Update owner's pet count
        updateOwnerPetCount(pet.getOwnerId());

        logger.info("Pet {} registered successfully for owner {}", petId, pet.getOwnerId());

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
            
            logger.debug("Owner {} verified as active", ownerId);
        } catch (Exception e) {
            logger.error("Failed to verify owner: {}", ownerId, e);
            throw new RuntimeException("Owner verification failed: " + e.getMessage(), e);
        }
    }

    private String generateUniquePetId() {
        return "PET-" + String.format("%03d", petIdCounter.getAndIncrement());
    }

    private void updateOwnerPetCount(String ownerId) {
        try {
            ModelSpec ownerModelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> ownerResponse = entityService.findByBusinessId(
                ownerModelSpec, ownerId, "ownerId", Owner.class);
            
            if (ownerResponse != null) {
                Owner owner = ownerResponse.entity();
                Integer currentCount = owner.getTotalPets() != null ? owner.getTotalPets() : 0;
                owner.setTotalPets(currentCount + 1);
                
                // Update owner without transition (loop back to same state)
                entityService.update(ownerResponse.getId(), owner, null);
                
                logger.debug("Updated pet count for owner {} to {}", ownerId, owner.getTotalPets());
            }
        } catch (Exception e) {
            logger.error("Failed to update owner pet count for: {}", ownerId, e);
            // Don't fail the entire registration for this
        }
    }
}
