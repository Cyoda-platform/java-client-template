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
 * RestaurantReactivationCriterion - Ensures restaurant can be safely reactivated
 * Transition: SUSPENDED → ACTIVE
 */
@Component
public class RestaurantReactivationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public RestaurantReactivationCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking restaurant reactivation criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Restaurant.class, this::validateRestaurantReactivation)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateRestaurantReactivation(CriterionSerializer.CriterionEntityEvaluationContext<Restaurant> context) {
        Restaurant restaurant = context.entityWithMetadata().entity();

        // Check if entity is null
        if (restaurant == null) {
            logger.warn("Restaurant entity is null");
            return EvaluationOutcome.fail("Restaurant entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify restaurant is currently suspended (isActive should be false)
        if (restaurant.getIsActive() == null || restaurant.getIsActive()) {
            return EvaluationOutcome.fail("Restaurant must be suspended to reactivate", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify address is still valid
        if (restaurant.getAddress() == null) {
            return EvaluationOutcome.fail("Restaurant address is required for reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Restaurant.RestaurantAddress address = restaurant.getAddress();
        if (address.getLine1() == null || address.getLine1().trim().isEmpty() ||
            address.getCity() == null || address.getCity().trim().isEmpty() ||
            address.getState() == null || address.getState().trim().isEmpty() ||
            address.getPostcode() == null || address.getPostcode().trim().isEmpty() ||
            address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            return EvaluationOutcome.fail("Complete address is required for reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify contact information is current
        if (restaurant.getContact() == null) {
            return EvaluationOutcome.fail("Contact information is required for reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Restaurant.RestaurantContact contact = restaurant.getContact();
        if (contact.getPhone() == null || contact.getPhone().trim().isEmpty() ||
            contact.getEmail() == null || contact.getEmail().trim().isEmpty()) {
            return EvaluationOutcome.fail("Valid contact information is required for reactivation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if restaurant has active menu items
        if (!hasActiveMenuItems(restaurant.getRestaurantId())) {
            return EvaluationOutcome.fail("Restaurant must have at least one active menu item", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Note: In a real implementation, we would check for outstanding compliance issues
        // This would involve checking external systems or compliance databases

        logger.debug("Restaurant reactivation criteria passed for: {}", restaurant.getName());
        return EvaluationOutcome.success();
    }

    private boolean hasActiveMenuItems(String restaurantId) {
        try {
            ModelSpec menuItemModelSpec = new ModelSpec()
                    .withName(MenuItem.ENTITY_NAME)
                    .withVersion(MenuItem.ENTITY_VERSION);

            SimpleCondition restaurantCondition = new SimpleCondition()
                    .withJsonPath("$.restaurantId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(restaurantId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(restaurantCondition));

            List<EntityWithMetadata<MenuItem>> menuItems = entityService.search(menuItemModelSpec, condition, MenuItem.class);

            // Check if any menu items are in ACTIVE state
            return menuItems.stream()
                    .anyMatch(menuItemWithMetadata -> "ACTIVE".equals(menuItemWithMetadata.getState()));

        } catch (Exception e) {
            logger.error("Error checking active menu items for restaurant {}: {}", restaurantId, e.getMessage());
            return false;
        }
    }
}
