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
public class SendFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SendFailureCriterion(SerializerFactory serializerFactory) {
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

         if (subscriber == null) {
             logger.warn("Subscriber entity is null in SendFailureCriterion");
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Subscriber must be active to attempt sending notifications
         if (subscriber.getActive() == null || !subscriber.getActive()) {
             return EvaluationOutcome.fail("Subscriber is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // contactType must be present
         String contactType = subscriber.getContactType();
         if (contactType == null || contactType.isBlank()) {
             return EvaluationOutcome.fail("contactType is required for sending notifications", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // contactAddress must be present
         String contactAddress = subscriber.getContactAddress();
         if (contactAddress == null || contactAddress.isBlank()) {
             return EvaluationOutcome.fail("contactAddress is required for sending notifications", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic quality checks depending on contactType
         if ("email".equalsIgnoreCase(contactType)) {
             if (!contactAddress.contains("@") || contactAddress.startsWith("@") || contactAddress.endsWith("@")) {
                 return EvaluationOutcome.fail("Invalid email address for subscriber", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else if ("webhook".equalsIgnoreCase(contactType)) {
             String lower = contactAddress.toLowerCase();
             if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                 return EvaluationOutcome.fail("Invalid webhook URL for subscriber; must start with http:// or https://", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else {
             // Unknown contact type - cannot attempt delivery
             return EvaluationOutcome.fail("Unsupported contactType: " + contactType, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If retry policy is provided, ensure it is sensible for send retries
         if (subscriber.getRetryPolicy() != null) {
             Integer maxAttempts = subscriber.getRetryPolicy().getMaxAttempts();
             Integer backoff = subscriber.getRetryPolicy().getBackoffSeconds();
             if (maxAttempts == null || maxAttempts < 1) {
                 return EvaluationOutcome.fail("retryPolicy.maxAttempts must be >= 1", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             if (backoff == null || backoff < 0) {
                 return EvaluationOutcome.fail("retryPolicy.backoffSeconds must be >= 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // All checks passed; sending may proceed
         return EvaluationOutcome.success();
    }
}