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
 * PetPerformanceAnalysisProcessor - Calculate performance metrics for pet products
 * Transition: analyze_performance (active → analyzed)
 */
@Component
public class PetPerformanceAnalysisProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetPerformanceAnalysisProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public PetPerformanceAnalysisProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetPerformanceAnalysis for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(PetEntity.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PetEntity> entityWithMetadata) {
        PetEntity entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<PetEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PetEntity> context) {

        EntityWithMetadata<PetEntity> entityWithMetadata = context.entityResponse();
        PetEntity entity = entityWithMetadata.entity();

        logger.debug("Analyzing performance for pet: {}", entity.getPetId());

        // Get all orders for current pet
        List<EntityWithMetadata<OrderEntity>> orders = getOrdersForPet(entity.getPetId());

        // Calculate performance metrics
        calculatePerformanceMetrics(entity, orders);

        logger.info("Performance analysis completed for pet: {}", entity.getPetId());

        return entityWithMetadata;
    }

    /**
     * Get all orders for the specified pet
     */
    private List<EntityWithMetadata<OrderEntity>> getOrdersForPet(Long petId) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(OrderEntity.ENTITY_NAME)
                    .withVersion(OrderEntity.ENTITY_VERSION);

            ObjectMapper objectMapper = new ObjectMapper();
            SimpleCondition simpleCondition = new SimpleCondition()
                    .withJsonPath("$.petId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(petId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(simpleCondition));

            return entityService.search(modelSpec, condition, OrderEntity.class);
        } catch (Exception e) {
            logger.warn("Failed to retrieve orders for pet {}: {}", petId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Calculate performance metrics based on orders
     */
    private void calculatePerformanceMetrics(PetEntity entity, List<EntityWithMetadata<OrderEntity>> orders) {
        if (orders.isEmpty()) {
            logger.debug("No orders found for pet: {}", entity.getPetId());
            return;
        }

        int totalSales = 0;
        double totalRevenue = 0.0;
        LocalDateTime lastSaleDate = null;

        for (EntityWithMetadata<OrderEntity> orderWithMetadata : orders) {
            OrderEntity order = orderWithMetadata.entity();
            
            if (order.getQuantity() != null) {
                totalSales += order.getQuantity();
            }
            
            if (order.getTotalAmount() != null) {
                totalRevenue += order.getTotalAmount();
            }
            
            if (order.getOrderDate() != null) {
                if (lastSaleDate == null || order.getOrderDate().isAfter(lastSaleDate)) {
                    lastSaleDate = order.getOrderDate();
                }
            }
        }

        // Update current entity with calculated metrics
        entity.setSalesVolume(totalSales);
        entity.setRevenue(totalRevenue);
        entity.setLastSaleDate(lastSaleDate);
        entity.setUpdatedAt(LocalDateTime.now());

        logger.debug("Performance metrics calculated for pet {}: sales={}, revenue={}, lastSale={}", 
                entity.getPetId(), totalSales, totalRevenue, lastSaleDate);
    }
}
