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
 * OrderDeliveryProcessor - Processes order delivery
 * 
 * Transition: deliver_order (approved → delivered)
 * Purpose: Processes delivery and updates associated pet to sold
 */
@Component
public class OrderDeliveryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderDeliveryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderDeliveryProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order delivery for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null && "approved".equals(currentState);
    }

    /**
     * Main business logic processing method
     * Processes delivery and updates associated pet
     */
    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing order delivery: {} in state: {}", order.getOrderId(), currentState);

        // Set delivery timestamp
        order.setShipDate(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setComplete(true);

        // Update associated pet to sold state
        updateAssociatedPet(order);

        logger.info("Order {} delivered successfully", order.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Update associated pet to sold state
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
                
                // Complete sale if pet is pending
                if ("pending".equals(petState)) {
                    pet.setUpdatedAt(LocalDateTime.now());
                    entityService.update(petWithMetadata.metadata().getId(), pet, "complete_sale");
                    logger.debug("Completed sale for pet {} in order {}", pet.getPetId(), order.getOrderId());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not update associated pet for order delivery {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}
