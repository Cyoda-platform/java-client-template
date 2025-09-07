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
 * OwnerClosureProcessor - Handles owner account closure business logic
 * 
 * Permanently closes owner account, including:
 * - Archiving all owned pets
 * - Canceling all pending orders
 * - Making account read-only for historical data
 */
@Component
public class OwnerClosureProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerClosureProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OwnerClosureProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner closure for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Owner.class)
            .validate(this::isValidEntityWithMetadata, "Invalid owner closure data")
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
        return owner != null && owner.isValid() && 
               ("ACTIVE".equals(currentState) || "SUSPENDED".equals(currentState));
    }

    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {
        
        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing owner closure for: {} in state: {}", owner.getOwnerId(), currentState);

        // 1. Archive all owned pets
        archiveAllOwnedPets(owner.getOwnerId());

        // 2. Cancel all pending orders
        cancelAllPendingOrders(owner.getOwnerId());

        // 3. Account becomes read-only for historical data
        logger.info("Owner {} account closed successfully", owner.getOwnerId());

        return entityWithMetadata;
    }

    private void archiveAllOwnedPets(String ownerId) {
        try {
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            
            // Search for pets owned by this owner that are not already ARCHIVED
            SearchConditionRequest petSearchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.ownerId", "EQUALS", ownerId),
                Condition.lifecycle("state", "EQUALS", "REGISTERED"),
                Condition.lifecycle("state", "EQUALS", "ACTIVE"),
                Condition.lifecycle("state", "EQUALS", "INACTIVE")
            );

            List<EntityWithMetadata<Pet>> petsToArchive = entityService.search(
                petModelSpec, petSearchRequest, Pet.class);
            
            // Archive each pet
            for (EntityWithMetadata<Pet> petResponse : petsToArchive) {
                try {
                    Pet pet = petResponse.entity();
                    
                    // Archive pet using transition
                    entityService.update(petResponse.getId(), pet, "archive_pet");
                    
                    logger.debug("Archived pet {} for closed owner {}", pet.getPetId(), ownerId);
                } catch (Exception e) {
                    logger.error("Failed to archive pet {} for owner {}", petResponse.entity().getPetId(), ownerId, e);
                    // Continue with other pets
                }
            }
            
            logger.debug("Archived {} pets for owner {}", petsToArchive.size(), ownerId);
        } catch (Exception e) {
            logger.error("Failed to archive pets for owner: {}", ownerId, e);
            // Don't fail the entire closure for this
        }
    }

    private void cancelAllPendingOrders(String ownerId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            
            // Search for orders with this ownerId in PENDING or CONFIRMED states
            SearchConditionRequest orderSearchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.ownerId", "EQUALS", ownerId),
                Condition.lifecycle("state", "EQUALS", "PENDING"),
                Condition.lifecycle("state", "EQUALS", "CONFIRMED")
            );

            List<EntityWithMetadata<PetCareOrder>> ordersToCancel = entityService.search(
                orderModelSpec, orderSearchRequest, PetCareOrder.class);
            
            // Cancel each order
            for (EntityWithMetadata<PetCareOrder> orderResponse : ordersToCancel) {
                try {
                    PetCareOrder order = orderResponse.entity();
                    order.setNotes("Order cancelled due to owner account closure");
                    
                    // Cancel order using transition
                    entityService.update(orderResponse.getId(), order, "cancel_order");
                    
                    logger.debug("Cancelled order {} for closed owner {}", order.getOrderId(), ownerId);
                } catch (Exception e) {
                    logger.error("Failed to cancel order {} for owner {}", orderResponse.entity().getOrderId(), ownerId, e);
                    // Continue with other orders
                }
            }
            
            logger.debug("Cancelled {} orders for owner {}", ordersToCancel.size(), ownerId);
        } catch (Exception e) {
            logger.error("Failed to cancel orders for owner: {}", ownerId, e);
            // Don't fail the entire closure for this
        }
    }
}
