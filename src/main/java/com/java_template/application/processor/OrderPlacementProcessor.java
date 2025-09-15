package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.menuitem.version_1.MenuItem;
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
 * OrderPlacementProcessor - Handles order placement workflow transition
 * Transition: none → PENDING
 */
@Component
public class OrderPlacementProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OrderPlacementProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderPlacementProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing order placement for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Order.class)
                .validate(this::isValidEntityWithMetadata, "Invalid order entity wrapper")
                .map(this::processOrderPlacement)
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

    private EntityWithMetadata<Order> processOrderPlacement(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Order> context) {

        EntityWithMetadata<Order> entityWithMetadata = context.entityResponse();
        Order order = entityWithMetadata.entity();

        logger.debug("Processing order placement: {}", order.getOrderId());

        // Set timestamps
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setPaymentStatus("PENDING");

        // Calculate order totals
        calculateOrderTotals(order);

        // Estimate delivery time
        estimateDeliveryTime(order);

        logger.info("Order placed: {} for restaurant: {}", order.getOrderId(), order.getRestaurantId());
        return entityWithMetadata;
    }

    private void calculateOrderTotals(Order order) {
        double subtotal = 0.0;

        if (order.getItems() != null) {
            for (Order.OrderItem item : order.getItems()) {
                // Validate menu item exists and is available
                validateMenuItem(item.getMenuItemId());
                
                // Calculate item total
                double itemTotal = item.getUnitPrice() * item.getQuantity();
                
                // Add customization costs
                if (item.getCustomizations() != null) {
                    for (Order.OrderItemCustomization customization : item.getCustomizations()) {
                        if (customization.getAdditionalPrice() != null) {
                            itemTotal += customization.getAdditionalPrice() * item.getQuantity();
                        }
                    }
                }
                
                item.setItemTotal(itemTotal);
                subtotal += itemTotal;
            }
        }

        order.setSubtotal(subtotal);
        
        // Calculate tax (10% tax rate)
        double tax = subtotal * 0.1;
        order.setTax(tax);
        
        // Calculate total amount
        double totalAmount = subtotal;
        if (order.getDeliveryFee() != null) totalAmount += order.getDeliveryFee();
        if (order.getServiceFee() != null) totalAmount += order.getServiceFee();
        if (order.getTip() != null) totalAmount += order.getTip();
        totalAmount += tax;
        
        order.setTotalAmount(totalAmount);
    }

    private void validateMenuItem(String menuItemId) {
        try {
            ModelSpec menuItemModelSpec = new ModelSpec()
                    .withName(MenuItem.ENTITY_NAME)
                    .withVersion(MenuItem.ENTITY_VERSION);

            SimpleCondition menuItemCondition = new SimpleCondition()
                    .withJsonPath("$.menuItemId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(menuItemId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(menuItemCondition));

            List<EntityWithMetadata<MenuItem>> menuItems = entityService.search(menuItemModelSpec, condition, MenuItem.class);

            if (menuItems.isEmpty()) {
                throw new IllegalArgumentException("Menu item " + menuItemId + " not found");
            }

            MenuItem menuItem = menuItems.get(0).entity();
            if (!menuItem.getIsAvailable()) {
                throw new IllegalArgumentException("Menu item " + menuItemId + " is not available");
            }

        } catch (Exception e) {
            logger.error("Error validating menu item {}: {}", menuItemId, e.getMessage());
            throw new IllegalArgumentException("Menu item " + menuItemId + " is not available");
        }
    }

    private void estimateDeliveryTime(Order order) {
        try {
            // Get restaurant average preparation time
            ModelSpec restaurantModelSpec = new ModelSpec()
                    .withName(Restaurant.ENTITY_NAME)
                    .withVersion(Restaurant.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(order.getRestaurantId()));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(restaurantModelSpec, condition, Restaurant.class);

            if (!restaurants.isEmpty()) {
                Restaurant restaurant = restaurants.get(0).entity();
                int prepTime = restaurant.getAveragePreparationTime() != null ? restaurant.getAveragePreparationTime() : 30;
                
                // Add 30 minutes for delivery
                LocalDateTime estimatedTime = LocalDateTime.now().plusMinutes(prepTime + 30);
                order.setEstimatedDeliveryTime(estimatedTime);
            }

        } catch (Exception e) {
            logger.warn("Could not estimate delivery time for order {}: {}", order.getOrderId(), e.getMessage());
            // Set default estimate of 60 minutes
            order.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(60));
        }
    }
}
