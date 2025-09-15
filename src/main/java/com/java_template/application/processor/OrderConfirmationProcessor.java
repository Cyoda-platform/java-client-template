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
 * OrderConfirmationProcessor - Handles order confirmation workflow transition
 * Transition: PENDING → CONFIRMED
 */
@Component
public class OrderConfirmationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderConfirmationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderConfirmationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order confirmation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processOrderConfirmation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Order> entityWithMetadata) {
        Order entity = entityWithMetadata.entity();
        return entity != null && entity.isValid();
    }

    private EntityWithMetadata<Order> processOrderConfirmation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing order confirmation: {}", order.getOrderId());

        // Update timestamps and payment status
        order.setUpdatedAt(LocalDateTime.now());
        order.setPaymentStatus("CONFIRMED");

        // Update estimated delivery time based on current restaurant load
        updateEstimatedDeliveryTime(order);

        // Note: In a real implementation, we would send notification to customer
        logger.info("Order confirmed: {}", order.getOrderId());
        
        return entityWithMetadata;
    }

    private void updateEstimatedDeliveryTime(Order order) {
        try {
            // Get restaurant information
            ModelSpec restaurantModelSpec = new ModelSpec()
                    .withName(Restaurant.ENTITY_NAME)
                    .withVersion(Restaurant.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(order.getRestaurantId()));

            GroupCondition restaurantSearchCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(restaurantModelSpec, restaurantSearchCondition, Restaurant.class);

            if (!restaurants.isEmpty()) {
                Restaurant restaurant = restaurants.get(0).entity();
                int prepTime = restaurant.getAveragePreparationTime() != null ? restaurant.getAveragePreparationTime() : 30;

                // Count active orders for this restaurant to calculate queue delay
                ModelSpec orderModelSpec = new ModelSpec()
                        .withName(Order.ENTITY_NAME)
                        .withVersion(Order.ENTITY_VERSION);

                List<EntityWithMetadata<Order>> activeOrders = entityService.search(orderModelSpec, restaurantSearchCondition, Order.class);
                
                long confirmedOrPreparingOrders = activeOrders.stream()
                        .filter(orderWithMetadata -> {
                            String state = orderWithMetadata.getState();
                            return "CONFIRMED".equals(state) || "PREPARING".equals(state);
                        })
                        .count();

                // Add 5 minutes delay per active order
                int queueDelay = (int) (confirmedOrPreparingOrders * 5);
                
                // Calculate new estimated delivery time
                LocalDateTime estimatedTime = LocalDateTime.now()
                        .plusMinutes(prepTime + queueDelay + 30); // 30 minutes for delivery
                
                order.setEstimatedDeliveryTime(estimatedTime);
            }

        } catch (Exception e) {
            logger.warn("Could not update estimated delivery time for order {}: {}", order.getOrderId(), e.getMessage());
        }
    }
}
