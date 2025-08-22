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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class ValidationPassedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public ValidationPassedCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber entity = context.entity();
         if (entity == null) {
             logger.warn("ValidationPassedCriterion: subscriber entity is null");
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required basic fields
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getContactMethod() == null || entity.getContactMethod().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.contactMethod is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getContactDetails() == null || entity.getContactDetails().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.contactDetails is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCreatedTimestamp() == null || entity.getCreatedTimestamp().isBlank()) {
             return EvaluationOutcome.fail("Subscriber.createdTimestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rules for contact method and details
         String method = entity.getContactMethod().toLowerCase(Locale.ROOT).trim();
         String details = entity.getContactDetails().trim();

         switch (method) {
             case "email":
                 if (!EMAIL_PATTERN.matcher(details).matches()) {
                     return EvaluationOutcome.fail("Invalid email address for contactDetails", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 break;
             case "webhook":
                 try {
                     URI uri = new URI(details);
                     String scheme = uri.getScheme();
                     if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                         return EvaluationOutcome.fail("Webhook URL must use http or https scheme", StandardEvalReasonCategories.VALIDATION_FAILURE);
                     }
                     if (uri.getHost() == null || uri.getHost().isBlank()) {
                         return EvaluationOutcome.fail("Webhook URL must contain a valid host", StandardEvalReasonCategories.VALIDATION_FAILURE);
                     }
                 } catch (URISyntaxException e) {
                     return EvaluationOutcome.fail("Invalid webhook URL for contactDetails", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 break;
             default:
                 return EvaluationOutcome.fail("Unsupported contactMethod: " + entity.getContactMethod(), StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If preference is present, validate allowed values (optional field)
         if (entity.getPreference() != null && !entity.getPreference().isBlank()) {
             String pref = entity.getPreference().toLowerCase(Locale.ROOT).trim();
             if (!(pref.equals("immediate") || pref.equals("dailydigest") || pref.equals("weeklydigest"))) {
                 // Not a hard failure, but mark as data quality issue
                 return EvaluationOutcome.fail("Unsupported preference value: " + entity.getPreference(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}