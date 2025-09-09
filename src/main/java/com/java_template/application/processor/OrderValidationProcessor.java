package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order_entity.version_1.OrderEntity;
import com.java_template.application.entity.pet_entity.version_1.PetEntity;
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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * OrderValidationProcessor - Validate order data and ensure pet relationship exists
 * Transition: validate_order (imported → validated)
 */
@Component
public class OrderValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderValidationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing OrderValidation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(OrderEntity.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<OrderEntity> entityWithMetadata) {
        OrderEntity entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    private EntityWithMetadata<OrderEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<OrderEntity> context) {

        EntityWithMetadata<OrderEntity> entityWithMetadata = context.entityResponse();
        OrderEntity entity = entityWithMetadata.entity();

        logger.debug("Validating order data for entity: {}", entity.getOrderId());

        // Validate current entity data
        validateOrderData(entity);

        // Verify pet relationship exists
        verifyPetRelationship(entity);

        logger.info("Order validation completed for entity: {}", entity.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Validate order data integrity and business rules
     */
    private void validateOrderData(OrderEntity entity) {
        if (entity.getOrderId() == null || entity.getPetId() == null) {
            throw new IllegalArgumentException("Order ID and Pet ID are required fields");
        }

        if (entity.getQuantity() == null || entity.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (entity.getUnitPrice() == null || entity.getUnitPrice() < 0) {
            throw new IllegalArgumentException("Unit price must be non-negative");
        }

        if (entity.getTotalAmount() == null || entity.getTotalAmount() < 0) {
            throw new IllegalArgumentException("Total amount must be non-negative");
        }

        if (entity.getOrderDate() == null) {
            throw new IllegalArgumentException("Order date is required");
        }

        // Validate calculated total amount
        double expectedTotal = entity.getQuantity() * entity.getUnitPrice();
        if (Math.abs(entity.getTotalAmount() - expectedTotal) > 0.01) {
            throw new IllegalArgumentException("Total amount does not match quantity * unit price");
        }

        // Check if order date is not in the future
        if (entity.getOrderDate().isAfter(java.time.LocalDateTime.now())) {
            throw new IllegalArgumentException("Order date cannot be in the future");
        }

        // Validate ship date if present
        if (entity.getShipDate() != null && entity.getShipDate().isBefore(entity.getOrderDate())) {
            throw new IllegalArgumentException("Ship date cannot be before order date");
        }

        logger.debug("Order data validation passed for entity: {}", entity.getOrderId());
    }

    /**
     * Verify that the referenced pet exists
     */
    private void verifyPetRelationship(OrderEntity entity) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(PetEntity.ENTITY_NAME)
                    .withVersion(PetEntity.ENTITY_VERSION);

            ObjectMapper objectMapper = new ObjectMapper();
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entity.getPetId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<PetEntity>> pets = entityService.search(modelSpec, condition, PetEntity.class);
            
            if (pets.isEmpty()) {
                throw new IllegalArgumentException("Referenced pet with ID " + entity.getPetId() + " not found");
            }

            logger.debug("Pet relationship verified for order: {} -> pet: {}", 
                    entity.getOrderId(), entity.getPetId());

        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            logger.error("Failed to verify pet relationship for order {}: {}", entity.getOrderId(), e.getMessage());
            throw new RuntimeException("Pet relationship verification failed", e);
        }
    }
}
