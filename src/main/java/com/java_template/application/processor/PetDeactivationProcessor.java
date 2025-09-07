package com.java_template.application.processor;

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
 * PetDeactivationProcessor - Handles pet deactivation business logic
 * 
 * Temporarily deactivates a pet, including:
 * - Checking for active orders
 * - Preventing deactivation if active orders exist
 * - Making pet ineligible for new service orders
 */
@Component
public class PetDeactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetDeactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetDeactivationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet deactivation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntityWithMetadata(Pet.class)
            .validate(this::isValidEntityWithMetadata, "Invalid pet deactivation data")
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
        return pet != null && pet.isValid() && "ACTIVE".equals(currentState);
    }

    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {
        
        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();
        String currentState = entityWithMetadata.getState();

        logger.debug("Processing pet deactivation for: {} in state: {}", pet.getPetId(), currentState);

        // 1. Check for active orders
        checkForActiveOrders(pet.getPetId());

        // 2. Pet becomes ineligible for new service orders
        logger.info("Pet {} deactivated successfully", pet.getPetId());

        return entityWithMetadata;
    }

    private void checkForActiveOrders(String petId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec().withName(PetCareOrder.ENTITY_NAME).withVersion(PetCareOrder.ENTITY_VERSION);
            
            // Search for orders with this petId in active states
            SearchConditionRequest searchRequest = SearchConditionRequest.group("AND",
                Condition.of("$.petId", "EQUALS", petId),
                Condition.lifecycle("state", "EQUALS", "PENDING"),
                Condition.lifecycle("state", "EQUALS", "CONFIRMED"),
                Condition.lifecycle("state", "EQUALS", "IN_PROGRESS")
            );

            List<EntityWithMetadata<PetCareOrder>> activeOrders = entityService.search(
                orderModelSpec, searchRequest, PetCareOrder.class);
            
            if (!activeOrders.isEmpty()) {
                logger.warn("Pet {} has {} active orders, preventing deactivation", petId, activeOrders.size());
                throw new IllegalStateException("Cannot deactivate pet with active orders. Found " + 
                    activeOrders.size() + " active orders.");
            }
            
            logger.debug("No active orders found for pet {}, deactivation can proceed", petId);
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw e;
            }
            logger.error("Failed to check active orders for pet: {}", petId, e);
            throw new RuntimeException("Failed to verify active orders: " + e.getMessage(), e);
        }
    }
}
