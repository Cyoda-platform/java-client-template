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
import java.util.Set;

@Component
public class OrderAdoptedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public OrderAdoptedCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order order = context.entity();

         if (order == null) {
             logger.warn("Order entity is null in context");
             return EvaluationOutcome.fail("Order entity missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic required checks (use only existing properties)
         if (order.getId() == null || order.getId().isBlank()) {
             return EvaluationOutcome.fail("Order.id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getUserId() == null || order.getUserId().isBlank()) {
             return EvaluationOutcome.fail("Order.userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getPetId() == null || order.getPetId().isBlank()) {
             return EvaluationOutcome.fail("Order.petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getType() == null || order.getType().isBlank()) {
             return EvaluationOutcome.fail("Order.type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getStatus() == null || order.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Order.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (order.getCreatedAt() == null || order.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("Order.createdAt is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (order.getExpiresAt() == null || order.getExpiresAt().isBlank()) {
             // Note: entity currently requires expiresAt; but if missing it's a data quality issue
             return EvaluationOutcome.fail("Order.expiresAt is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (order.getTotal() == null) {
             return EvaluationOutcome.fail("Order.total is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (order.getTotal() < 0) {
             return EvaluationOutcome.fail("Order.total must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate allowed order types
         Set<String> allowedTypes = Set.of("adopt", "purchase", "reserve");
         if (!allowedTypes.contains(order.getType().toLowerCase())) {
             return EvaluationOutcome.fail("Order.type contains unsupported value: " + order.getType(),
                 StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate allowed statuses according to functional requirements
         Set<String> allowedStatuses = Set.of(
             "initiated", "validation_failed", "pending_verification", "payment_pending",
             "payment_failed", "approved", "staff_review", "completed", "cancelled", "expired"
         );
         if (!allowedStatuses.contains(order.getStatus().toLowerCase())) {
             return EvaluationOutcome.fail("Order.status contains unsupported value: " + order.getStatus(),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Validate timestamps are ISO-8601 parsable and logical (createdAt <= expiresAt)
         Instant createdInstant;
         Instant expiresInstant;
         try {
             createdInstant = Instant.parse(order.getCreatedAt());
         } catch (DateTimeParseException e) {
             return EvaluationOutcome.fail("Order.createdAt is not a valid ISO-8601 timestamp: " + order.getCreatedAt(),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         try {
             expiresInstant = Instant.parse(order.getExpiresAt());
         } catch (DateTimeParseException e) {
             return EvaluationOutcome.fail("Order.expiresAt is not a valid ISO-8601 timestamp: " + order.getExpiresAt(),
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (createdInstant.isAfter(expiresInstant)) {
             return EvaluationOutcome.fail("Order.createdAt must be before or equal to Order.expiresAt",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule checks specific to adopt orders
         if ("adopt".equalsIgnoreCase(order.getType())) {
             // Adopt orders must progress through allowed workflow states.
             // If an adopt order is marked as 'completed' it is expected to be final.
             // We can't inspect Pet state here, but we can ensure current order state is consistent.
             Set<String> terminalStates = Set.of("completed", "cancelled", "expired", "validation_failed", "payment_failed");
             if (!terminalStates.contains(order.getStatus().toLowerCase())) {
                 // For adopt orders not yet terminal, ensure there is a sensible total (already checked) and expiresAt in future.
                 if (expiresInstant.isBefore(Instant.now())) {
                     return EvaluationOutcome.fail("Adopt order has already expired (expiresAt in the past) but is not in an expiry state",
                         StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             } else {
                 // If completed, ensure total is non-negative (already validated) and createdAt is not in the future
                 if (createdInstant.isAfter(Instant.now().plusSeconds(5))) {
                     return EvaluationOutcome.fail("Order.createdAt is in the future", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}