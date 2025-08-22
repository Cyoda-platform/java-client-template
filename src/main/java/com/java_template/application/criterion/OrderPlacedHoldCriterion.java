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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Order.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order entity = context.entity();

         // Required references
         if (entity.getPetId() == null || entity.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required for placing a hold", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getUserId() == null || entity.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required for placing a hold", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Type must be one of allowed order types that can place a hold
         Set<String> allowedTypes = Set.of("adopt", "purchase", "reserve");
         if (entity.getType() == null || entity.getType().isBlank()) {
             return EvaluationOutcome.fail("order type is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!allowedTypes.contains(entity.getType())) {
             return EvaluationOutcome.fail("unsupported order type for hold: " + entity.getType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Order must be in an initial state to attempt placing a hold
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("order status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Business rule: a hold should be attempted only for newly created/initiated orders
         if (!"initiated".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("order must be in 'initiated' state to place a hold", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Total sanity: for 'purchase' require a positive total; others may be zero or positive
         if ("purchase".equalsIgnoreCase(entity.getType())) {
             if (entity.getTotal() == null || entity.getTotal() <= 0) {
                 return EvaluationOutcome.fail("purchase orders must have a positive total", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } else {
             // ensure total is non-negative and present
             if (entity.getTotal() == null || entity.getTotal() < 0) {
                 return EvaluationOutcome.fail("order total must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If expiresAt is provided, ensure it is a valid ISO-8601 timestamp and is after createdAt if createdAt exists
         if (entity.getExpiresAt() != null && !entity.getExpiresAt().isBlank()) {
             try {
                 Instant expires = Instant.parse(entity.getExpiresAt());
                 if (entity.getCreatedAt() != null && !entity.getCreatedAt().isBlank()) {
                     try {
                         Instant created = Instant.parse(entity.getCreatedAt());
                         if (!expires.isAfter(created)) {
                             return EvaluationOutcome.fail("expiresAt must be after createdAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                         }
                     } catch (Exception e) {
                         return EvaluationOutcome.fail("createdAt is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                     }
                 }
             } catch (Exception e) {
                 return EvaluationOutcome.fail("expiresAt is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}