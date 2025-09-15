package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.restaurant.version_1.Restaurant;
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
 * RestaurantSuspensionProcessor - Handles restaurant suspension workflow transition
 * Transition: ACTIVE → SUSPENDED
 */
@Component
public class RestaurantSuspensionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RestaurantSuspensionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public RestaurantSuspensionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing restaurant suspension for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Restaurant.class)
                .validate(this::isValidEntityWithMetadata, "Invalid restaurant entity wrapper")
                .map(this::processRestaurantSuspension)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Restaurant> entityWithMetadata) {
        Restaurant entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<Restaurant> processRestaurantSuspension(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Restaurant> context) {

        EntityWithMetadata<Restaurant> entityWithMetadata = context.entityResponse();
        Restaurant restaurant = entityWithMetadata.entity();

        logger.debug("Processing restaurant suspension: {}", restaurant.getRestaurantId());

        // Deactivate restaurant
        restaurant.setIsActive(false);
        restaurant.setUpdatedAt(LocalDateTime.now());

        // Cancel all pending orders for this restaurant
        cancelPendingOrders(restaurant.getRestaurantId());

        // Note: In a real implementation, we would notify delivery services about restaurant suspension
        logger.info("Restaurant suspended: {}", restaurant.getName());
        
        return entityWithMetadata;
    }

    private void cancelPendingOrders(String restaurantId) {
        try {
            ModelSpec orderModelSpec = new ModelSpec()
                    .withName(Order.ENTITY_NAME)
                    .withVersion(Order.ENTITY_VERSION);

            // Create condition to find pending/confirmed orders for this restaurant
            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Order>> pendingOrders = entityService.search(orderModelSpec, condition, Order.class);

            for (EntityWithMetadata<Order> orderWithMetadata : pendingOrders) {
                String orderState = orderWithMetadata.getState();
                if ("PENDING".equals(orderState) || "CONFIRMED".equals(orderState)) {
                    // Cancel the order using transition
                    entityService.update(orderWithMetadata.getId(), orderWithMetadata.entity(), "cancel_order");
                    logger.info("Cancelled order {} due to restaurant suspension", orderWithMetadata.entity().getOrderId());
                }
            }
        } catch (Exception e) {
            logger.error("Error cancelling pending orders for restaurant {}: {}", restaurantId, e.getMessage());
        }
    }
}
