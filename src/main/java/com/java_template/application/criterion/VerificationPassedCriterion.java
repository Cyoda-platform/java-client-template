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

@Component
public class VerificationPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public VerificationPassedCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber subscriber = context.entity();
         if (subscriber == null) {
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required fields presence checks
         if (subscriber.getId() == null || subscriber.getId().isBlank()) {
             return EvaluationOutcome.fail("Subscriber id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getCreatedAt() == null || subscriber.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getActive() == null) {
             return EvaluationOutcome.fail("active flag must be present", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) {
             return EvaluationOutcome.fail("contactType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getContactDetail() == null || subscriber.getContactDetail().isBlank()) {
             return EvaluationOutcome.fail("contactDetail is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules: verification implies subscriber is active
         if (!Boolean.TRUE.equals(subscriber.getActive())) {
             return EvaluationOutcome.fail("Subscriber is not active (verification not passed)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate contact detail according to contact type
         String type = subscriber.getContactType().trim().toLowerCase();
         String detail = subscriber.getContactDetail().trim();
         switch (type) {
             case "email":
                 // basic email validation: contains '@' and a dot after '@'
                 int at = detail.indexOf('@');
                 if (at <= 0 || at == detail.length() - 1) {
                     return EvaluationOutcome.fail("Invalid email address", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
                 if (detail.indexOf('.', at) == -1) {
                     return EvaluationOutcome.fail("Invalid email address", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
                 break;
             case "webhook":
                 // basic webhook URL validation
                 String lower = detail.toLowerCase();
                 if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                     return EvaluationOutcome.fail("Invalid webhook URL (must start with http:// or https://)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
                 break;
             default:
                 return EvaluationOutcome.fail("Unsupported contactType: " + subscriber.getContactType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}