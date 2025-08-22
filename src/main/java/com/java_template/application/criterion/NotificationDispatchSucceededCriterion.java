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

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class NotificationDispatchSucceededCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Pattern EMAIL_SIMPLE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public NotificationDispatchSucceededCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber subscriber = context.entity();

         // Basic null checks for required fields
         if (subscriber == null) {
             logger.debug("Subscriber entity is null");
             return EvaluationOutcome.fail("Subscriber entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (subscriber.getName() == null || subscriber.getName().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (subscriber.getContactMethod() == null || subscriber.getContactMethod().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.contactMethod is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (subscriber.getContactDetails() == null || subscriber.getContactDetails().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.contactDetails is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (subscriber.getStatus() == null || subscriber.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks for contact method and details
         String method = subscriber.getContactMethod().trim().toLowerCase(Locale.ROOT);
         String details = subscriber.getContactDetails().trim();

         if ("email".equals(method)) {
             if (!EMAIL_SIMPLE.matcher(details).matches()) {
                 return EvaluationOutcome.fail("Invalid email address in contactDetails", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else if ("webhook".equals(method) || "http".equals(method) || "https".equals(method)) {
             String lower = details.toLowerCase(Locale.ROOT);
             if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                 return EvaluationOutcome.fail("Invalid webhook URL in contactDetails (must start with http:// or https://)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else {
             return EvaluationOutcome.fail("Unsupported contactMethod: " + subscriber.getContactMethod(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: subscriber must be ACTIVE to receive notifications
         // Allowed statuses per requirements: active, paused, unsubscribed
         if (!"active".equalsIgnoreCase(subscriber.getStatus())) {
             return EvaluationOutcome.fail("Subscriber not active - notifications cannot be dispatched to this subscriber", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If preference provided, validate allowed values (optional field per requirements)
         if (subscriber.getPreference() != null && !subscriber.getPreference().isBlank()) {
             String pref = subscriber.getPreference().trim().toLowerCase(Locale.ROOT);
             if (!(Objects.equals(pref, "immediate") || Objects.equals(pref, "dailydigest") || Objects.equals(pref, "weeklydigest")
                   || Objects.equals(pref, "daily") || Objects.equals(pref, "weekly"))) { // accept common variants
                 return EvaluationOutcome.fail("Unknown preference value: " + subscriber.getPreference(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Passed all checks
         return EvaluationOutcome.success();
    }
}