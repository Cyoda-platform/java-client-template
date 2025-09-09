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

import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderProcessingProcessor - Process order for analytics and update pet performance
 * Transition: process_order (validated → processed)
 */
@Component
public class OrderProcessingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public OrderProcessingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing OrderProcessing for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<OrderEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<OrderEntity> context) {

        EntityWithMetadata<OrderEntity> entityWithMetadata = context.entityResponse();
        OrderEntity entity = entityWithMetadata.entity();

        logger.debug("Processing order for analytics: {}", entity.getOrderId());

        // Update pet performance metrics
        updatePetPerformance(entity);

        logger.info("Order processing completed for entity: {}", entity.getOrderId());

        return entityWithMetadata;
    }

    /**
     * Update pet performance metrics based on this order
     */
    private void updatePetPerformance(OrderEntity order) {
        try {
            // Find the pet associated with this order
            EntityWithMetadata<PetEntity> petWithMetadata = findPetById(order.getPetId());
            
            if (petWithMetadata == null) {
                logger.warn("Pet not found for order {}: petId={}", order.getOrderId(), order.getPetId());
                return;
            }

            PetEntity pet = petWithMetadata.entity();
            
            // Update pet performance metrics
            updatePetMetrics(pet, order);
            
            // Save updated pet entity (no state transition)
            entityService.update(petWithMetadata.metadata().getId(), pet, null);
            
            logger.debug("Updated pet performance for petId={}: sales={}, revenue={}", 
                    pet.getPetId(), pet.getSalesVolume(), pet.getRevenue());

        } catch (Exception e) {
            logger.error("Failed to update pet performance for order {}: {}", order.getOrderId(), e.getMessage());
            // Don't throw exception - order processing should continue even if pet update fails
        }
    }

    /**
     * Find pet entity by pet ID
     */
    private EntityWithMetadata<PetEntity> findPetById(Long petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(PetEntity.ENTITY_NAME)
                    .withVersion(PetEntity.ENTITY_VERSION);

            ObjectMapper objectMapper = new ObjectMapper();
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(petId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            List<EntityWithMetadata<PetEntity>> pets = entityService.search(modelSpec, condition, PetEntity.class);
            
            return pets.isEmpty() ? null : pets.get(0);

        } catch (Exception e) {
            logger.error("Failed to find pet by ID {}: {}", petId, e.getMessage());
            return null;
        }
    }

    /**
     * Update pet metrics with order data
     */
    private void updatePetMetrics(PetEntity pet, OrderEntity order) {
        // Increment sales volume
        int currentSalesVolume = pet.getSalesVolume() != null ? pet.getSalesVolume() : 0;
        int orderQuantity = order.getQuantity() != null ? order.getQuantity() : 0;
        pet.setSalesVolume(currentSalesVolume + orderQuantity);

        // Increment revenue
        double currentRevenue = pet.getRevenue() != null ? pet.getRevenue() : 0.0;
        double orderAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0.0;
        pet.setRevenue(currentRevenue + orderAmount);

        // Update last sale date
        if (order.getOrderDate() != null) {
            if (pet.getLastSaleDate() == null || order.getOrderDate().isAfter(pet.getLastSaleDate())) {
                pet.setLastSaleDate(order.getOrderDate());
            }
        }

        // Update timestamp
        pet.setUpdatedAt(LocalDateTime.now());
    }
}
