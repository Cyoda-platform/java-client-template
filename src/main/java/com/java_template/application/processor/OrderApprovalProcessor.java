package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.user.version_1.User;
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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * OrderApprovalProcessor - Approve order after validation
 * 
 * Transition: approve_order (placed → approved)
 * Purpose: Approve order after validation
 */
@Component
public class OrderApprovalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderApprovalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderApprovalProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Order approval for request: {}", request.getId());

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

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Order> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing order approval: {}", order.getOrderId());

        // 1. Validate user exists using entityService
        try {
            ModelSpec userModelSpec = new ModelSpec()
                    .withName(User.ENTITY_NAME)
                    .withVersion(User.ENTITY_VERSION);
            
            EntityWithMetadata<User> userEntity = entityService.findByBusinessId(
                    userModelSpec, order.getUserId(), "userId", User.class);
            
            if (userEntity == null || !"active".equals(userEntity.metadata().getState())) {
                logger.error("User not found or not active for order {}: {}", order.getOrderId(), order.getUserId());
                return entityWithMetadata; // Return order unchanged
            }
        } catch (Exception e) {
            logger.error("Error validating user for order {}: {}", order.getOrderId(), e.getMessage());
            return entityWithMetadata;
        }

        // 3. Validate all pets in order items are in 'pending' state
        if (order.getItems() != null) {
            for (Order.OrderItem item : order.getItems()) {
                try {
                    ModelSpec petModelSpec = new ModelSpec()
                            .withName(Pet.ENTITY_NAME)
                            .withVersion(Pet.ENTITY_VERSION);
                    
                    EntityWithMetadata<Pet> petEntity = entityService.findByBusinessId(
                            petModelSpec, item.getPetId(), "petId", Pet.class);
                    
                    if (petEntity == null || !"pending".equals(petEntity.metadata().getState())) {
                        logger.warn("Pet {} is not in pending state for order {}", item.getPetId(), order.getOrderId());
                    }
                } catch (Exception e) {
                    logger.error("Error validating pet {} for order {}: {}", 
                            item.getPetId(), order.getOrderId(), e.getMessage());
                }
            }
        }

        // 4. Calculate and verify total amount matches sum of item prices
        if (order.getItems() != null) {
            double calculatedTotal = order.getItems().stream()
                    .mapToDouble(item -> item.getTotalPrice() != null ? item.getTotalPrice() : 0.0)
                    .sum();
            
            if (Math.abs(calculatedTotal - order.getTotalAmount()) > 0.01) {
                logger.warn("Total amount mismatch for order {}: calculated={}, order={}", 
                        order.getOrderId(), calculatedTotal, order.getTotalAmount());
            }
        }

        // 5. Set updatedAt = current timestamp
        order.setUpdatedAt(LocalDateTime.now());

        logger.info("Order {} approved successfully", order.getOrderId());
        return entityWithMetadata;
    }
}
