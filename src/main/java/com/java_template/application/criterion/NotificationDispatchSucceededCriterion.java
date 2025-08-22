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
public class NotificationDispatchSucceededCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NotificationDispatchSucceededCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("Subscriber entity is null in NotificationDispatchSucceededCriterion");
             return EvaluationOutcome.fail("Subscriber entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Subscriber must be ACTIVE to receive notifications
         String status = subscriber.getStatus();
         if (status == null || !status.equalsIgnoreCase("active")) {
             return EvaluationOutcome.fail("Subscriber is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // contactMethod must be present
         String contactMethod = subscriber.getContactMethod();
         if (contactMethod == null || contactMethod.isBlank()) {
             return EvaluationOutcome.fail("Contact method is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // contactDetails must be present
         String contactDetails = subscriber.getContactDetails();
         if (contactDetails == null || contactDetails.isBlank()) {
             return EvaluationOutcome.fail("Contact details are required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate contact details format based on method
         if ("email".equalsIgnoreCase(contactMethod)) {
             // simple email pattern check
             String email = contactDetails.trim();
             String simpleEmailRegex = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
             if (!email.matches(simpleEmailRegex)) {
                 return EvaluationOutcome.fail("Invalid email address", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else if ("webhook".equalsIgnoreCase(contactMethod)) {
             String url = contactDetails.trim().toLowerCase();
             if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                 return EvaluationOutcome.fail("Webhook URL must start with http:// or https://", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else {
             return EvaluationOutcome.fail("Unsupported contact method: " + contactMethod, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If preference is present, ensure it is one of known values (immediate, dailyDigest, weeklyDigest)
         String preference = subscriber.getPreference();
         if (preference != null && !preference.isBlank()) {
             String p = preference.trim().toLowerCase();
             if (!("immediate".equals(p) || "dailydigest".equals(p) || "weeklydigest".equals(p) || "dailyDigest".equals(preference) || "weeklyDigest".equals(preference))) {
                 // treat unknown preference as data quality issue but do not block notification if other fields are valid
                 return EvaluationOutcome.fail("Unknown preference value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}