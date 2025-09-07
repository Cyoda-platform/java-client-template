package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.petcareorder.version_1.PetCareOrder;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * PetArchivalProcessor - Handles pet archival business logic
 * 
 * Permanently archives a pet, including:
 * - Canceling any pending orders
 * - Updating owner's pet count
 * - Making pet read-only for historical data
 */
@Component
public class PetArchivalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetArchivalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetArchivalProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet archival for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Pet.class)
            .validate(this::isValidEntityWithMetadata, "Invalid pet archival data")
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
        return pet != null && pet.isValid() && 
               ("ACTIVE".equals(currentState) || "INACTIVE".equals(currentState));
    }

    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {
        
        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing pet archival for: {} in state: {}", pet.getPetId(), currentState);

        // 1. Cancel any pending orders
        cancelPendingOrders(pet.getPetId());

        // 2. Update owner's pet count
        updateOwnerPetCount(pet.getOwnerId());

        // 3. Pet becomes read-only for historical data
        logger.info("Pet {} archived successfully", pet.getPetId());

        return entityWithMetadata;
    }

    private void cancelPendingOrders(String petId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            
            // Search for orders with this petId in PENDING or CONFIRMED states
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.petId", "EQUALS", petId),
                Condition.lifecycle("state", "EQUALS", "PENDING"),
                Condition.lifecycle("state", "EQUALS", "CONFIRMED")
            );

            List<EntityWithMetadata<PetCareOrder>> pendingOrders = entityService.search(
                orderModelSpec, searchRequest, PetCareOrder.class);
            
            // Cancel each pending order
            for (EntityWithMetadata<PetCareOrder> orderResponse : pendingOrders) {
                try {
                    PetCareOrder order = orderResponse.entity();
                    order.setNotes("Order cancelled due to pet archival");
                    
                    // Cancel order using transition
                    entityService.update(orderResponse.getId(), order, "cancel_order");
                    
                    logger.debug("Cancelled order {} for archived pet {}", order.getOrderId(), petId);
                } catch (Exception e) {
                    logger.error("Failed to cancel order {} for pet {}", orderResponse.entity().getOrderId(), petId, e);
                    // Continue with other orders
                }
            }
            
            logger.debug("Cancelled {} pending orders for pet {}", pendingOrders.size(), petId);
        } catch (Exception e) {
            logger.error("Failed to cancel pending orders for pet: {}", petId, e);
            // Don't fail the entire archival for this
        }
    }

    private void updateOwnerPetCount(String ownerId) {
        try {
            ModelSpec ownerModelSpec = new ModelSpec().withName(Owner.ENTITY_NAME).withVersion(Owner.ENTITY_VERSION);
            EntityWithMetadata<Owner> ownerResponse = entityService.findByBusinessId(
                ownerModelSpec, ownerId, "ownerId", Owner.class);
            
            if (ownerResponse != null) {
                Owner owner = ownerResponse.entity();
                Integer currentCount = owner.getTotalPets() != null ? owner.getTotalPets() : 0;
                owner.setTotalPets(Math.max(0, currentCount - 1));
                
                // Update owner without transition (loop back to same state)
                entityService.update(ownerResponse.getId(), owner, null);
                
                logger.debug("Updated pet count for owner {} to {}", ownerId, owner.getTotalPets());
            }
        } catch (Exception e) {
            logger.error("Failed to update owner pet count for: {}", ownerId, e);
            // Don't fail the entire archival for this
        }
    }
}
