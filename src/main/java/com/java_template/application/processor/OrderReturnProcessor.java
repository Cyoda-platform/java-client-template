package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * OrderReturnProcessor - Processes order return
 * 
 * Transition: return_order (delivered → returned)
 * Purpose: Processes return and makes associated pet available again
 */
@Component
public class OrderReturnProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderReturnProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderReturnProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order return for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();
        return entity != null && entity.isValid() && technicalId != null && "delivered".equals(currentState);
    }

    /**
     * Main business logic processing method
     * Processes return and updates associated pet
     */
    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing order return: {} in state: {}", order.getOrderId(), currentState);

        // Update timestamps
        order.setUpdatedAt(LocalDateTime.now());

        // Update associated pet to available state
        updateAssociatedPet(order);

        logger.info("Order {} returned successfully", order.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Update associated pet to available state
     */
    private void updateAssociatedPet(Order order) {
        try {
            // Find the pet
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petWithMetadata = entityService.findByBusinessId(
                    petModelSpec, order.getPetId(), "petId", Pet.class);

            if (petWithMetadata != null) {
                Pet pet = petWithMetadata.entity();
                String petState = petWithMetadata.metadata().getState();
                
                // Return pet if it's sold
                if ("sold".equals(petState)) {
                    pet.setUpdatedAt(LocalDateTime.now());
                    entityService.update(petWithMetadata.metadata().getId(), pet, "return_pet");
                    logger.debug("Returned pet {} for order {}", pet.getPetId(), order.getOrderId());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not update associated pet for order return {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}
