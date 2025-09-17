package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OrderCreationProcessor - Creates and validates a new order
 * 
 * Transition: create_order (none → placed)
 * Purpose: Creates new order and validates customer and pet availability
 */
@Component
public class OrderCreationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderCreationProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order creation for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     * Creates order and validates customer and pet
     */
    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing order creation: {} in state: {}", order.getOrderId(), currentState);

        // Set timestamps
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Generate unique orderId if not provided
        if (order.getOrderId() == null || order.getOrderId().trim().isEmpty()) {
            order.setOrderId("order-" + UUID.randomUUID().toString().substring(0, 8));
        }

        // Calculate total amount based on pet price
        calculateTotalAmount(order);

        // Update associated pet to pending state
        updateAssociatedPet(order);

        logger.info("Order {} created successfully", order.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Calculate total amount based on pet price and quantity
     */
    private void calculateTotalAmount(Order order) {
        try {
            // Find the pet to get its price
            ModelSpec petModelSpec = new ModelSpec().withName(Pet.ENTITY_NAME).withVersion(Pet.ENTITY_VERSION);
            EntityWithMetadata<Pet> petWithMetadata = entityService.findByBusinessId(
                    petModelSpec, order.getPetId(), "petId", Pet.class);

            if (petWithMetadata != null && petWithMetadata.entity().getPrice() != null) {
                Double petPrice = petWithMetadata.entity().getPrice();
                Integer quantity = order.getQuantity() != null ? order.getQuantity() : 1;
                order.setTotalAmount(petPrice * quantity);
                logger.debug("Calculated total amount: {} for order {}", order.getTotalAmount(), order.getOrderId());
            }
        } catch (Exception e) {
            logger.warn("Could not calculate total amount for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }

    /**
     * Update associated pet to pending state
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
                
                // Reserve pet if it's available
                if ("available".equals(petState)) {
                    pet.setUpdatedAt(LocalDateTime.now());
                    entityService.update(petWithMetadata.metadata().getId(), pet, "reserve_pet");
                    logger.debug("Reserved pet {} for order {}", pet.getPetId(), order.getOrderId());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not update associated pet for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}
