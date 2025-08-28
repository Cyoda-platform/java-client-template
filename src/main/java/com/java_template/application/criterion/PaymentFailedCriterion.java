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

@Component
public class PaymentFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(PaymentFailedCriterion.class);
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PaymentFailedCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name match (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Order> context) {
         Order entity = context.entity();

         if (entity == null) {
             logger.warn("Order entity is null in PaymentFailedCriterion");
             return EvaluationOutcome.fail("Order entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required buyer information for payment processing
         if (entity.getBuyerContact() == null || entity.getBuyerContact().isBlank()) {
             return EvaluationOutcome.fail("buyerContact is required for payment validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getBuyerName() == null || entity.getBuyerName().isBlank()) {
             return EvaluationOutcome.fail("buyerName is required for payment validation", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getPlacedAt() == null || entity.getPlacedAt().isBlank()) {
             return EvaluationOutcome.fail("placedAt (order timestamp) is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic data quality check for buyerContact:
         // Accept either an email-like string containing '@' or a phone-like string with digits.
         String contact = entity.getBuyerContact();
         boolean looksLikeEmail = contact.contains("@");
         boolean looksLikePhone = contact.chars().anyMatch(Character::isDigit);
         if (!looksLikeEmail && !looksLikePhone) {
             return EvaluationOutcome.fail("buyerContact does not appear to be a valid email or phone number", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass, this criterion does not indicate payment failure
         return EvaluationOutcome.success();
    }
}