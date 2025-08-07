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
public class ValidateSubscriberEmail implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateSubscriberEmail(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
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
         // Validate that contactEmail is not null and not blank
         if (subscriber.getContactEmail() == null || subscriber.getContactEmail().isBlank()) {
            return EvaluationOutcome.fail("Contact email is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Validate email format (simple regex for demonstration)
         if (!subscriber.getContactEmail().matches("^[\\w-\\.+]+@[\\w-]+\\.[a-z]{2,}$")) {
            return EvaluationOutcome.fail("Contact email format is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // Validate webhookUrl if present: must not be blank if set
         if (subscriber.getWebhookUrl() != null && subscriber.getWebhookUrl().isBlank()) {
            return EvaluationOutcome.fail("Webhook URL, if provided, must not be blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Validate active flag must not be null
         if (subscriber.getActive() == null) {
            return EvaluationOutcome.fail("Active flag must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         return EvaluationOutcome.success();
    }
}
