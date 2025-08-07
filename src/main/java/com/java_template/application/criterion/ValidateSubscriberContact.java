package com.java_template.application.criterion;

import com.java_template.application.entity.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidateSubscriberContact implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateSubscriberContact(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request)
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

         // Validation: subscriberId must not be null or blank
         if (subscriber.getSubscriberId() == null || subscriber.getSubscriberId().isBlank()) {
            return EvaluationOutcome.fail("subscriberId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validation: contactType must not be null or blank
         if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) {
            return EvaluationOutcome.fail("contactType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validation: contactValue must not be null or blank
         if (subscriber.getContactValue() == null || subscriber.getContactValue().isBlank()) {
            return EvaluationOutcome.fail("contactValue is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validation: subscribedAt must not be null or blank
         if (subscriber.getSubscribedAt() == null || subscriber.getSubscribedAt().isBlank()) {
            return EvaluationOutcome.fail("subscribedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Additional business rule: contactType must be either "email" or "webhook"
         String type = subscriber.getContactType();
         if (!type.equalsIgnoreCase("email") && !type.equalsIgnoreCase("webhook")) {
             return EvaluationOutcome.fail("contactType must be either 'email' or 'webhook'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Additional business rule: contactValue format validation
         if (type.equalsIgnoreCase("email")) {
             if (!subscriber.getContactValue().contains("@")) {
                 return EvaluationOutcome.fail("Invalid email address format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else if (type.equalsIgnoreCase("webhook")) {
             if (!subscriber.getContactValue().startsWith("http://") && !subscriber.getContactValue().startsWith("https://")) {
                 return EvaluationOutcome.fail("Invalid webhook URL format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}
