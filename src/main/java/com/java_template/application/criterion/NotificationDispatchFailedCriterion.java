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
import java.util.regex.Pattern;

@Component
public class NotificationDispatchFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern SIMPLE_URL = Pattern.compile("^(https?)://.+", Pattern.CASE_INSENSITIVE);

    public NotificationDispatchFailedCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber entity = context.entity();

         if (entity == null) {
             logger.warn("Subscriber entity is null in context");
             return EvaluationOutcome.fail("Subscriber entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate required contact method
         String contactMethod = entity.getContactMethod();
         if (contactMethod == null || contactMethod.isBlank()) {
             return EvaluationOutcome.fail("contactMethod is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String method = contactMethod.trim().toLowerCase(Locale.ROOT);
         if (!method.equals("email") && !method.equals("webhook")) {
             return EvaluationOutcome.fail("unsupported contactMethod: " + contactMethod, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate contact details presence
         String contactDetails = entity.getContactDetails();
         if (contactDetails == null || contactDetails.isBlank()) {
             return EvaluationOutcome.fail("contactDetails is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic data quality checks depending on method
         if (method.equals("email")) {
             if (!SIMPLE_EMAIL.matcher(contactDetails.trim()).matches()) {
                 return EvaluationOutcome.fail("invalid email address", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else { // webhook
             if (!SIMPLE_URL.matcher(contactDetails.trim()).matches()) {
                 return EvaluationOutcome.fail("invalid webhook URL", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule: subscriber must be active to receive notifications
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("subscriber status missing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         String st = status.trim().toLowerCase(Locale.ROOT);
         if (st.equals("paused") || st.equals("unsubscribed") || st.equals("failed")) {
             return EvaluationOutcome.fail("subscriber not in active state: " + status, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If all checks pass, mark as success (no dispatch failure reason found here)
         return EvaluationOutcome.success();
    }
}