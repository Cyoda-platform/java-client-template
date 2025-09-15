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

import java.util.Arrays;
import java.util.List;

/**
 * MenuItemValidationCriterion - Validates menu item is ready for publication
 * Transition: DRAFT → ACTIVE
 */
@Component
public class MenuItemValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final List<String> VALID_CATEGORIES = Arrays.asList(
            "Appetizers", "Main Course", "Desserts", "Beverages", "Pizza", "Pasta", 
            "Salads", "Soups", "Sandwiches", "Burgers", "Seafood", "Vegetarian", "Vegan"
    );

    public MenuItemValidationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking menu item validation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(MenuItem.class, this::validateMenuItemPublication)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateMenuItemPublication(CriterionSerializer.CriterionEntityEvaluationContext<MenuItem> context) {
        MenuItem menuItem = context.entityWithMetadata().entity();

        // Check if entity is null
        if (menuItem == null) {
            logger.warn("MenuItem entity is null");
            return EvaluationOutcome.fail("MenuItem entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify menu item name is not empty
        if (menuItem.getName() == null || menuItem.getName().trim().isEmpty()) {
            return EvaluationOutcome.fail("Menu item name cannot be empty", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify menu item description is not empty
        if (menuItem.getDescription() == null || menuItem.getDescription().trim().isEmpty()) {
            return EvaluationOutcome.fail("Menu item description cannot be empty", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify category is valid
        if (menuItem.getCategory() == null || !VALID_CATEGORIES.contains(menuItem.getCategory())) {
            return EvaluationOutcome.fail("Menu item category must be one of: " + String.join(", ", VALID_CATEGORIES), 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify price is positive
        if (menuItem.getPrice() == null || menuItem.getPrice() <= 0) {
            return EvaluationOutcome.fail("Menu item price must be greater than 0", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify preparation time is non-negative if specified
        if (menuItem.getPreparationTime() != null && menuItem.getPreparationTime() < 0) {
            return EvaluationOutcome.fail("Preparation time cannot be negative", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify parent restaurant is active
        if (!isParentRestaurantActive(menuItem.getRestaurantId())) {
            return EvaluationOutcome.fail("Parent restaurant must be active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check for duplicate menu items
        if (hasDuplicateMenuItem(menuItem.getRestaurantId(), menuItem.getName())) {
            return EvaluationOutcome.fail("Menu item with this name already exists for this restaurant", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        logger.debug("Menu item validation criteria passed for: {}", menuItem.getName());
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

    private boolean hasDuplicateMenuItem(String restaurantId, String menuItemName) {
        try {
            ModelSpec menuItemModelSpec = new ModelSpec()
                    .withName(MenuItem.ENTITY_NAME)
                    .withVersion(MenuItem.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            SimpleCondition nameCondition = new SimpleCondition()
                    .withJsonPath("$.name")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(menuItemName));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition, nameCondition));

            List<EntityWithMetadata<MenuItem>> existingItems = entityService.search(menuItemModelSpec, condition, MenuItem.class);

            // Check if any existing items are in ACTIVE state
            return existingItems.stream()
                    .anyMatch(itemWithMetadata -> "ACTIVE".equals(itemWithMetadata.getState()));

        } catch (Exception e) {
            logger.error("Error checking for duplicate menu items: {}", e.getMessage());
            return false;
        }
    }
}
