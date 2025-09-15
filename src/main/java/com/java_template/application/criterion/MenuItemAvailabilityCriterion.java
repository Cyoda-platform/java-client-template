package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.menuitem.version_1.MenuItem;
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
 * MenuItemAvailabilityCriterion - Ensures menu item can be made available again
 * Transition: UNAVAILABLE → ACTIVE
 */
@Component
public class MenuItemAvailabilityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MenuItemAvailabilityCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking menu item availability criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(MenuItem.class, this::validateMenuItemAvailability)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateMenuItemAvailability(CriterionSerializer.CriterionEntityEvaluationContext<MenuItem> context) {
        MenuItem menuItem = context.entityWithMetadata().entity();

        // Check if entity is null
        if (menuItem == null) {
            logger.warn("MenuItem entity is null");
            return EvaluationOutcome.fail("MenuItem entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify parent restaurant is still active
        if (!isParentRestaurantActive(menuItem.getRestaurantId())) {
            return EvaluationOutcome.fail("Parent restaurant must be active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify menu item data is still valid
        if (menuItem.getPrice() == null || menuItem.getPrice() <= 0) {
            return EvaluationOutcome.fail("Menu item price must be greater than 0", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (menuItem.getName() == null || menuItem.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Menu item name cannot be empty", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if restaurant is currently accepting orders
        if (!isRestaurantAcceptingOrders(menuItem.getRestaurantId())) {
            return EvaluationOutcome.fail("Restaurant must be accepting orders", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Menu item availability criteria passed for: {}", menuItem.getName());
        return EvaluationOutcome.success();
    }

    private boolean isParentRestaurantActive(String restaurantId) {
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
            logger.error("Error checking parent restaurant status for {}: {}", restaurantId, e.getMessage());
            return false;
        }
    }

    private boolean isRestaurantAcceptingOrders(String restaurantId) {
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

            if (restaurants.isEmpty()) {
                return false;
            }

            Restaurant restaurant = restaurants.get(0).entity();
            return restaurant.getIsActive() != null && restaurant.getIsActive();

        } catch (Exception e) {
            logger.error("Error checking if restaurant is accepting orders for {}: {}", restaurantId, e.getMessage());
            return false;
        }
    }
}
