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

import java.util.regex.Pattern;

@Component
public class IsContactValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Simple email pattern: ensures basic local@domain.tld structure
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public IsContactValidCriterion(SerializerFactory serializerFactory) {
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
             logger.debug("Subscriber entity is null");
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String email = subscriber.getEmail();
         String webhook = subscriber.getWebhookUrl();

         boolean hasEmail = email != null && !email.isBlank();
         boolean hasWebhook = webhook != null && !webhook.isBlank();

         // At least one contact method must be present
         if (!hasEmail && !hasWebhook) {
             return EvaluationOutcome.fail("No contact provided: email or webhookUrl required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If email provided, validate basic format
         if (hasEmail) {
             String trimmedEmail = email.trim();
             if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
                 return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // If webhook provided, ensure it looks like an HTTP(S) URL
         if (hasWebhook) {
             String trimmedWebhook = webhook.trim().toLowerCase();
             if (!(trimmedWebhook.startsWith("http://") || trimmedWebhook.startsWith("https://"))) {
                 return EvaluationOutcome.fail("Invalid webhookUrl: must start with http:// or https://", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}