package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.application.entity.pet.version_1.Pet;
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
 * OwnerSuspensionProcessor - Handles owner suspension business logic
 * 
 * Temporarily suspends owner account, including:
 * - Canceling pending orders
 * - Deactivating associated pets
 * - Suspending account access
 */
@Component
public class OwnerSuspensionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerSuspensionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OwnerSuspensionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner suspension for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Owner.class)
            .validate(this::isValidEntityWithMetadata, "Invalid owner suspension data")
            .map(this::processEntityWithMetadataLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Owner> entityWithMetadata) {
        Owner owner = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();
        return owner != null && owner.isValid() && "ACTIVE".equals(currentState);
    }

    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {
        
        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing owner suspension for: {} in state: {}", owner.getOwnerId(), currentState);

        // 1. Cancel pending orders
        cancelPendingOrders(owner.getOwnerId());

        // 2. Deactivate associated pets
        deactivateAssociatedPets(owner.getOwnerId());

        // 3. Owner cannot place new orders (handled by state change)
        logger.info("Owner {} suspended successfully", owner.getOwnerId());

        return entityWithMetadata;
    }

    private void cancelPendingOrders(String ownerId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            
            // Search for orders with this ownerId in PENDING state
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.ownerId", "EQUALS", ownerId),
                Condition.lifecycle("state", "EQUALS", "PENDING")
            );

            List<EntityWithMetadata<PetCareOrder>> pendingOrders = entityService.search(
                orderModelSpec, searchRequest, PetCareOrder.class);
            
            // Cancel each pending order
            for (EntityWithMetadata<PetCareOrder> orderResponse : pendingOrders) {
                try {
                    PetCareOrder order = orderResponse.entity();
                    order.setNotes("Order cancelled due to owner suspension");
                    
                    // Cancel order using transition
                    entityService.update(orderResponse.getId(), order, "cancel_order");
                    
                    logger.debug("Cancelled order {} for suspended owner {}", order.getOrderId(), ownerId);
                } catch (Exception e) {
                    logger.error("Failed to cancel order {} for owner {}", orderResponse.entity().getOrderId(), ownerId, e);
                    // Continue with other orders
                }
            }
            
            logger.debug("Cancelled {} pending orders for owner {}", pendingOrders.size(), ownerId);
        } catch (Exception e) {
            logger.error("Failed to cancel pending orders for owner: {}", ownerId, e);
            // Don't fail the entire suspension for this
        }
    }

    private void deactivateAssociatedPets(String ownerId) {
        try {
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            
            // Search for pets owned by this owner in ACTIVE state
            SearchConditionRequest petSearchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.ownerId", "EQUALS", ownerId),
                Condition.lifecycle("state", "EQUALS", "ACTIVE")
            );

            List<EntityWithMetadata<Pet>> activePets = entityService.search(
                petModelSpec, petSearchRequest, Pet.class);
            
            // Deactivate each active pet
            for (EntityWithMetadata<Pet> petResponse : activePets) {
                try {
                    Pet pet = petResponse.entity();
                    
                    // Deactivate pet using transition
                    entityService.update(petResponse.getId(), pet, "deactivate_pet");
                    
                    logger.debug("Deactivated pet {} for suspended owner {}", pet.getPetId(), ownerId);
                } catch (Exception e) {
                    logger.error("Failed to deactivate pet {} for owner {}", petResponse.entity().getPetId(), ownerId, e);
                    // Continue with other pets
                }
            }
            
            logger.debug("Deactivated {} pets for owner {}", activePets.size(), ownerId);
        } catch (Exception e) {
            logger.error("Failed to deactivate pets for owner: {}", ownerId, e);
            // Don't fail the entire suspension for this
        }
    }
}
