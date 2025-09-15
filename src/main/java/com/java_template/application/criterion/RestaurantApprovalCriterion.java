package com.java_template.application.criterion;

import com.java_template.application.entity.restaurant.version_1.Restaurant;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * RestaurantApprovalCriterion - Validates that restaurant meets approval requirements
 * Transition: PENDING_APPROVAL → ACTIVE
 */
@Component
public class RestaurantApprovalCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Email validation pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"
    );

    // Phone validation pattern (simplified)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[0-9\\s\\-()]{7,20}$"
    );

    public RestaurantApprovalCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking restaurant approval criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Restaurant.class, this::validateRestaurantApproval)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateRestaurantApproval(CriterionSerializer.CriterionEntityEvaluationContext<Restaurant> context) {
        Restaurant restaurant = context.entityWithMetadata().entity();

        // Check if entity is null
        if (restaurant == null) {
            logger.warn("Restaurant entity is null");
            return EvaluationOutcome.fail("Restaurant entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Verify restaurant name is not empty
        if (restaurant.getName() == null || restaurant.getName().trim().isEmpty()) {
            logger.warn("Restaurant name is empty");
            return EvaluationOutcome.fail("Restaurant name cannot be empty", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify address is complete
        if (restaurant.getAddress() == null) {
            logger.warn("Restaurant address is missing");
            return EvaluationOutcome.fail("Restaurant address is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Restaurant.RestaurantAddress address = restaurant.getAddress();
        if (address.getLine1() == null || address.getLine1().trim().isEmpty()) {
            return EvaluationOutcome.fail("Address line 1 is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (address.getCity() == null || address.getCity().trim().isEmpty()) {
            return EvaluationOutcome.fail("City is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (address.getState() == null || address.getState().trim().isEmpty()) {
            return EvaluationOutcome.fail("State is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (address.getPostcode() == null || address.getPostcode().trim().isEmpty()) {
            return EvaluationOutcome.fail("Postcode is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        if (address.getCountry() == null || address.getCountry().trim().isEmpty()) {
            return EvaluationOutcome.fail("Country is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify contact information
        if (restaurant.getContact() == null) {
            logger.warn("Restaurant contact information is missing");
            return EvaluationOutcome.fail("Restaurant contact information is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        Restaurant.RestaurantContact contact = restaurant.getContact();
        if (contact.getPhone() == null || !PHONE_PATTERN.matcher(contact.getPhone()).matches()) {
            return EvaluationOutcome.fail("Valid phone number is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
        if (contact.getEmail() == null || !EMAIL_PATTERN.matcher(contact.getEmail()).matches()) {
            return EvaluationOutcome.fail("Valid email address is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Verify operating hours has at least one day defined
        if (restaurant.getOperatingHours() == null || restaurant.getOperatingHours().isEmpty()) {
            return EvaluationOutcome.fail("At least one operating day must be defined", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Verify delivery zones (can use default zone if empty)
        // This is handled by the processor, so we just check it's not explicitly invalid
        if (restaurant.getDeliveryZones() != null) {
            for (Restaurant.DeliveryZone zone : restaurant.getDeliveryZones()) {
                if (zone.getRadius() != null && zone.getRadius() <= 0) {
                    return EvaluationOutcome.fail("Delivery zone radius must be positive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                }
            }
        }

        logger.debug("Restaurant approval criteria passed for: {}", restaurant.getName());
        return EvaluationOutcome.success();
    }
}
