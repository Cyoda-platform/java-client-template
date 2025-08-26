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
public class SendSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SendSuccessCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("Subscriber entity is null in SendSuccessCriterion");
             return EvaluationOutcome.fail("Subscriber entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // technicalId must be present for routing and auditing
         if (subscriber.getTechnicalId() == null || subscriber.getTechnicalId().isBlank()) {
             return EvaluationOutcome.fail("technicalId is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Active flag must be true to attempt sending
         if (subscriber.getActive() == null) {
             return EvaluationOutcome.fail("active flag is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!subscriber.getActive()) {
             return EvaluationOutcome.fail("subscriber is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // contactType and contactAddress required
         String contactType = subscriber.getContactType();
         if (contactType == null || contactType.isBlank()) {
             return EvaluationOutcome.fail("contactType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         contactType = contactType.trim().toLowerCase();

         String contactAddress = subscriber.getContactAddress();
         if (contactAddress == null || contactAddress.isBlank()) {
             return EvaluationOutcome.fail("contactAddress is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         contactAddress = contactAddress.trim();

         // Supported contact types: email or webhook
         if (!contactType.equals("email") && !contactType.equals("webhook")) {
             return EvaluationOutcome.fail("unsupported contactType: " + subscriber.getContactType(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Basic format checks for contactAddress depending on contactType
         if (contactType.equals("email")) {
             if (!contactAddress.contains("@") || contactAddress.startsWith("@") || contactAddress.endsWith("@")) {
                 return EvaluationOutcome.fail("invalid email address", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         } else { // webhook
             String lower = contactAddress.toLowerCase();
             if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                 return EvaluationOutcome.fail("invalid webhook URL; must start with http:// or https://", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Validate retry policy if present
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

         // Validate filters categories if present (data quality)
         if (subscriber.getFilters() != null && subscriber.getFilters().getCategories() != null) {
             for (String c : subscriber.getFilters().getCategories()) {
                 if (c == null || c.isBlank()) {
                     return EvaluationOutcome.fail("filters.categories contains blank entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         return EvaluationOutcome.success();
    }
}