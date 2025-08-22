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
public class NotificationDispatchFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NotificationDispatchFailedCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber subscriber = context.entity();

         // Required fields validation
         if (subscriber.getStatus() == null || subscriber.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getContactMethod() == null || subscriber.getContactMethod().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.contactMethod is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getContactDetails() == null || subscriber.getContactDetails().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.contactDetails is missing - cannot deliver notification", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: subscriber must be ACTIVE to receive notifications
         if (!"active".equalsIgnoreCase(subscriber.getStatus())) {
             String msg = String.format("Subscriber status is '%s' - notifications should only be dispatched to ACTIVE subscribers", subscriber.getStatus());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate supported contact methods and their basic formats
         String method = subscriber.getContactMethod().trim().toLowerCase();
         String details = subscriber.getContactDetails().trim();

         switch (method) {
             case "email":
                 // very basic email format check
                 if (!details.contains("@") || !details.contains(".")) {
                     return EvaluationOutcome.fail("Subscriber.contactDetails does not appear to be a valid email address", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 break;
             case "webhook":
                 // basic URL check for webhook endpoints
                 String lower = details.toLowerCase();
                 if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                     return EvaluationOutcome.fail("Subscriber.contactDetails does not appear to be a valid webhook URL (must start with http:// or https://)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 break;
             default:
                 return EvaluationOutcome.fail("Unsupported contactMethod: " + subscriber.getContactMethod(), StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        // All checks passed — dispatch should not fail due to subscriber data
        return EvaluationOutcome.success();
    }
}