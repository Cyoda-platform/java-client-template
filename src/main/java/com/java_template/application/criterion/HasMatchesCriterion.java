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
public class HasMatchesCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public HasMatchesCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match the exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber subscriber = context.entity();

         if (subscriber == null) {
             logger.warn("Subscriber entity is null in HasMatchesCriterion");
             return EvaluationOutcome.fail("Subscriber entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Subscriber must be active to have matches
         if (subscriber.getActive() == null || !subscriber.getActive()) {
             return EvaluationOutcome.fail("Subscriber is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Must have at least one subscribed category to match incoming laureates
         if (subscriber.getSubscribedCategories() == null || subscriber.getSubscribedCategories().isEmpty()) {
             return EvaluationOutcome.fail("Subscriber has no subscribed categories", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Ensure categories are non-blank
         for (String cat : subscriber.getSubscribedCategories()) {
             if (cat == null || cat.isBlank()) {
                 return EvaluationOutcome.fail("Subscriber has invalid subscribed category", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Contact must include a non-blank email to receive notifications
         Subscriber.Contact contact = subscriber.getContact();
         if (contact == null || contact.getEmail() == null || contact.getEmail().isBlank()) {
             return EvaluationOutcome.fail("Subscriber has no contact email", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Basic email format sanity check
         String email = contact.getEmail();
         int at = email.indexOf('@');
         if (at <= 0 || at == email.length() - 1) {
             logger.warn("Subscriber {} has an invalid email: {}", subscriber.getId(), email);
             return EvaluationOutcome.fail("Subscriber contact email is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // Ensure there's a domain with a dot after '@'
         String domain = email.substring(at + 1);
         if (!domain.contains(".")) {
             logger.warn("Subscriber {} has an invalid email domain: {}", subscriber.getId(), domain);
             return EvaluationOutcome.fail("Subscriber contact email domain is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If a year range is provided, both from and to must be present and non-blank and coherent
         Subscriber.YearRange yr = subscriber.getSubscribedYearRange();
         if (yr != null) {
             if (yr.getFrom() == null || yr.getFrom().isBlank() || yr.getTo() == null || yr.getTo().isBlank()) {
                 return EvaluationOutcome.fail("Subscriber subscribed year range is invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             // Try to interpret as integers and ensure from <= to; if not numeric, it's a data quality issue
             try {
                 int fromYear = Integer.parseInt(yr.getFrom());
                 int toYear = Integer.parseInt(yr.getTo());
                 if (fromYear > toYear) {
                     return EvaluationOutcome.fail("Subscriber subscribed year range 'from' is after 'to'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             } catch (NumberFormatException nfe) {
                 logger.warn("Subscriber {} has non-numeric year range: from='{}' to='{}'", subscriber.getId(), yr.getFrom(), yr.getTo());
                 return EvaluationOutcome.fail("Subscriber subscribed year range is not numeric", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If all configuration checks pass, the subscriber is considered able to have matches.
         return EvaluationOutcome.success();
    }
}