package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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

@Component
public class DeliverySuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public DeliverySuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match the exact criterion name
        return "DeliverySuccessCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber entity = context.entity();
         if (entity == null) {
             logger.warn("DeliverySuccessCriterion: Subscriber entity is null in context");
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identity and contact fields
         if (entity.getId() == null || entity.getId().isBlank()) {
             return EvaluationOutcome.fail("Subscriber id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getEmail() == null || entity.getEmail().isBlank()) {
             return EvaluationOutcome.fail("Subscriber email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Validate email format with a basic pattern (do not rely on external libs)
         if (!EMAIL_PATTERN.matcher(entity.getEmail().trim()).matches()) {
             return EvaluationOutcome.fail("Subscriber email format is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getActive() == null) {
             return EvaluationOutcome.fail("Subscriber active flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Business rule: subscriber must be active to be considered for successful delivery
         if (Boolean.FALSE.equals(entity.getActive())) {
             return EvaluationOutcome.fail("Subscriber is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Business rule: if subscriber opted out, deliveries should not be considered successful
         if (entity.getOptOutAt() != null && !entity.getOptOutAt().isBlank()) {
             return EvaluationOutcome.fail("Subscriber has opted out", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String status = entity.getLastDeliveryStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("lastDeliveryStatus is missing for subscriber", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Normalized checks for known delivery statuses
         String normalized = status.trim().toUpperCase();
         switch (normalized) {
             case "SUCCESS":
                 return EvaluationOutcome.success();
             case "FAILED":
                 return EvaluationOutcome.fail("Last delivery recorded as FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             case "PENDING":
                 return EvaluationOutcome.fail("Delivery is still pending", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             default:
                 return EvaluationOutcome.fail("Unknown lastDeliveryStatus: " + status, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
    }
}