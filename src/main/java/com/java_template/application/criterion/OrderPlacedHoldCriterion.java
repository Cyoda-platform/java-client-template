package com.java_template.application.criterion;

import com.java_template.application.entity.order.version_1.Order;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;

@Component
public class OrderPlacedHoldCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderPlacedHoldCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order order = context.entity();
         if (order == null) {
             logger.warn("Order entity is null in context for {}", className);
             return EvaluationOutcome.fail("Order entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required checks (use existing getters only)
         if (order.getId() == null || order.getId().isBlank()) {
             return EvaluationOutcome.fail("Order id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getPetId() == null || order.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getUserId() == null || order.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getType() == null || order.getType().isBlank()) {
             return EvaluationOutcome.fail("order type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getStatus() == null || order.getStatus().isBlank()) {
             return EvaluationOutcome.fail("order status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure order is in the expected starting status for placing a hold
         if (!"initiated".equals(order.getStatus())) {
             return EvaluationOutcome.fail("order must be in 'initiated' status to place a hold", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate allowed order types
         Set<String> allowedTypes = Set.of("adopt", "purchase", "reserve");
         String lowerType = order.getType().toLowerCase();
         if (!allowedTypes.contains(lowerType)) {
             return EvaluationOutcome.fail("unsupported order type: " + order.getType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Total should be non-negative (Order.isValid enforces non-null/non-negative) and for monetary flows should be > 0
         if (order.getTotal() == null) {
             return EvaluationOutcome.fail("order total is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getTotal() < 0) {
             return EvaluationOutcome.fail("order total must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (order.getTotal() == 0) {
             // Business decision: zero-value orders are likely invalid for holds/payments
             return EvaluationOutcome.fail("order total must be greater than zero to place a hold", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // For reservation flows, expiresAt must be present and in the future
         if ("reserve".equals(lowerType)) {
             if (order.getExpiresAt() == null || order.getExpiresAt().isBlank()) {
                 return EvaluationOutcome.fail("expiresAt is required for reserve orders", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             try {
                 Instant expires = Instant.parse(order.getExpiresAt());
                 Instant now = Instant.now();
                 if (!expires.isAfter(now)) {
                     return EvaluationOutcome.fail("expiresAt must be a future timestamp for reserve orders", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (DateTimeParseException e) {
                 logger.debug("Invalid expiresAt format for order {}: {}", order.getId(), order.getExpiresAt(), e);
                 return EvaluationOutcome.fail("expiresAt must be a valid ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } else {
             // If expiresAt provided for non-reserve types, ensure it is a valid ISO timestamp (optional quality check)
             if (order.getExpiresAt() != null && !order.getExpiresAt().isBlank()) {
                 try {
                     Instant.parse(order.getExpiresAt());
                 } catch (DateTimeParseException e) {
                     logger.debug("Invalid optional expiresAt format for order {}: {}", order.getId(), order.getExpiresAt(), e);
                     return EvaluationOutcome.fail("expiresAt, if provided, must be a valid ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
             }
         }

         // Notes: this criterion validates order shape and hold-related fields only.
         // Actual atomic hold acquisition is performed by the HoldPetProcessor / OrderValidationProcessor.
         // If all checks pass, the order is considered valid for attempting a hold.
         return EvaluationOutcome.success();
    }
}