package com.java_template.application.processor;

import com.java_template.application.entity.petcareorder.version_1.PetCareOrder;
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
 * OrderConfirmationProcessor - Handles order confirmation business logic
 * 
 * Confirms order after validation and scheduling, including:
 * - Validating order details
 * - Verifying pet and owner are still active
 * - Assigning service provider if needed
 * - Locking in scheduling
 */
@Component
public class OrderConfirmationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderConfirmationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderConfirmationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order confirmation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(PetCareOrder.class)
            .validate(this::isValidEntityWithMetadata, "Invalid order confirmation data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PetCareOrder> entityWithMetadata) {
        PetCareOrder order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();
        return order != null && order.isValid() && "PENDING".equals(currentState);
    }

    private EntityWithMetadata<PetCareOrder> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PetCareOrder> context) {
        
        EntityWithMetadata<PetCareOrder> entityWithMetadata = context.entityResponse();
        PetCareOrder order = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing order confirmation for: {} in state: {}", order.getOrderId(), currentState);

        // 1. Verify pet and owner are still active
        verifyPetAndOwnerStillActive(order.getPetId(), order.getOwnerId());

        // 2. Assign service provider if veterinary service
        if ("VETERINARY".equals(order.getServiceType()) && 
            (order.getVeterinarianName() == null || order.getVeterinarianName().trim().isEmpty())) {
            order.setVeterinarianName("Dr. Default Veterinarian");
        }

        // 3. Order becomes committed and resources are reserved
        logger.info("Order {} confirmed successfully", order.getOrderId());

        return entityWithMetadata;
    }

    private void verifyPetAndOwnerStillActive(String petId, String ownerId) {
        // Verify pet is still active
        EntityWithMetadata<Pet> petResponse = getPetByBusinessId(petId);
        if (petResponse == null) {
            throw new IllegalArgumentException("Pet not found: " + petId);
        }
        
        String petState = petResponse.getState();
        if (!"ACTIVE".equals(petState)) {
            throw new IllegalArgumentException("Pet is no longer active: " + petId + " (state: " + petState + ")");
        }

        // Verify owner is still active
        EntityWithMetadata<Owner> ownerResponse = getOwnerByBusinessId(ownerId);
        if (ownerResponse == null) {
            throw new IllegalArgumentException("Owner not found: " + ownerId);
        }
        
        String ownerState = ownerResponse.getState();
        if (!"ACTIVE".equals(ownerState)) {
            throw new IllegalArgumentException("Owner is no longer active: " + ownerId + " (state: " + ownerState + ")");
        }

        logger.debug("Pet {} and owner {} verified as still active for order confirmation", petId, ownerId);
    }

    private EntityWithMetadata<Pet> getPetByBusinessId(String petId) {
        try {
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            return entityService.findByBusinessId(petModelSpec, petId, "petId", Pet.class);
        } catch (Exception e) {
            logger.error("Failed to get pet: {}", petId, e);
            throw new RuntimeException("Pet lookup failed: " + e.getMessage(), e);
        }
    }

    private EntityWithMetadata<Owner> getOwnerByBusinessId(String ownerId) {
        try {
            ModelSpec ownerModelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            return entityService.findByBusinessId(ownerModelSpec, ownerId, "ownerId", Owner.class);
        } catch (Exception e) {
            logger.error("Failed to get owner: {}", ownerId, e);
            throw new RuntimeException("Owner lookup failed: " + e.getMessage(), e);
        }
    }
}
