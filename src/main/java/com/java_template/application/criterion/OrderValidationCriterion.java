package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.menuitem.version_1.MenuItem;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.restaurant.version_1.Restaurant;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OrderValidationCriterion - Validates order can be confirmed by restaurant
 * Transition: PENDING → CONFIRMED
 */
@Component
public class OrderValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public OrderValidationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking order validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Order.class, this::validateOrderConfirmation)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateOrderConfirmation(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
        Order order = context.entityWithMetadata().entity();

        // Check if entity is null
        if (order == null) {
            logger.warn("Order entity is null");
            return EvaluationOutcome.fail("Order entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify restaurant is active and accepting orders
        Restaurant restaurant = getRestaurant(order.getRestaurantId());
        if (restaurant == null) {
            return EvaluationOutcome.fail("Restaurant not found", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (!isRestaurantActive(order.getRestaurantId())) {
            return EvaluationOutcome.fail("Restaurant is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (restaurant.getIsActive() == null || !restaurant.getIsActive()) {
            return EvaluationOutcome.fail("Restaurant is not accepting orders", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify all menu items are still available
        if (order.getItems() != null) {
            for (Order.OrderItem item : order.getItems()) {
                if (!isMenuItemAvailable(item.getMenuItemId(), order.getRestaurantId())) {
                    return EvaluationOutcome.fail("Menu item " + item.getName() + " is no longer available", 
                            StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
        }

        // Verify order totals are correct
        if (!areOrderTotalsCorrect(order)) {
            return EvaluationOutcome.fail("Order totals are incorrect", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            return EvaluationOutcome.fail("Order total amount must be greater than 0", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify delivery address is in restaurant's delivery zones
        if (!isDeliveryAddressInZone(order, restaurant)) {
            return EvaluationOutcome.fail("Delivery address is not in restaurant's delivery zones", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Order validation criteria passed for: {}", order.getOrderId());
        return EvaluationOutcome.success();
    }

    private Restaurant getRestaurant(String restaurantId) {
        try {
            ModelSpec restaurantModelSpec = new ModelSpec()
                    .withName(Restaurant.ENTITY_NAME)
                    .withVersion(Restaurant.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(restaurantModelSpec, condition, Restaurant.class);

            return restaurants.isEmpty() ? null : restaurants.get(0).entity();

        } catch (Exception e) {
            logger.error("Error getting restaurant {}: {}", restaurantId, e.getMessage());
            return null;
        }
    }

    private boolean isRestaurantActive(String restaurantId) {
        try {
            ModelSpec restaurantModelSpec = new ModelSpec()
                    .withName(Restaurant.ENTITY_NAME)
                    .withVersion(Restaurant.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<Restaurant>> restaurants = entityService.search(restaurantModelSpec, condition, Restaurant.class);

            return !restaurants.isEmpty() && "ACTIVE".equals(restaurants.get(0).getState());

        } catch (Exception e) {
            logger.error("Error checking restaurant status for {}: {}", restaurantId, e.getMessage());
            return false;
        }
    }

    private boolean isMenuItemAvailable(String menuItemId, String restaurantId) {
        try {
            ModelSpec menuItemModelSpec = new ModelSpec()
                    .withName(MenuItem.ENTITY_NAME)
                    .withVersion(MenuItem.ENTITY_VERSION);

            SimpleCondition itemCondition = new SimpleCondition()
                    .withJsonPath("$.menuItemId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(menuItemId));

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(itemCondition, restaurantCondition));

            List<EntityWithMetadata<MenuItem>> menuItems = entityService.search(menuItemModelSpec, condition, MenuItem.class);

            if (menuItems.isEmpty()) {
                return false;
            }

            EntityWithMetadata<MenuItem> menuItemWithMetadata = menuItems.get(0);
            MenuItem menuItem = menuItemWithMetadata.entity();
            
            return "ACTIVE".equals(menuItemWithMetadata.getState()) && 
                   menuItem.getIsAvailable() != null && menuItem.getIsAvailable();

        } catch (Exception e) {
            logger.error("Error checking menu item availability for {}: {}", menuItemId, e.getMessage());
            return false;
        }
    }

    private boolean areOrderTotalsCorrect(Order order) {
        if (order.getItems() == null || order.getSubtotal() == null) {
            return false;
        }

        double expectedSubtotal = order.getItems().stream()
                .mapToDouble(item -> item.getItemTotal() != null ? item.getItemTotal() : 0.0)
                .sum();

        // Allow small floating point differences
        return Math.abs(expectedSubtotal - order.getSubtotal()) < 0.01;
    }

    private boolean isDeliveryAddressInZone(Order order, Restaurant restaurant) {
        // Simplified implementation - in reality would calculate distance
        // For now, just check if delivery zones exist (if none, assume default zone covers all)
        if (restaurant.getDeliveryZones() == null || restaurant.getDeliveryZones().isEmpty()) {
            return true; // Default zone covers all
        }

        // In a real implementation, would calculate distance from restaurant to delivery address
        // and check if it's within any of the delivery zones' radius
        return true;
    }
}
