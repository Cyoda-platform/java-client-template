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
public class SubscriberNotificationEventCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubscriberNotificationEventCriterion(SerializerFactory serializerFactory) {
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
         Subscriber entity = context.entity();
         if (entity == null) {
             logger.warn("Subscriber entity is null in context");
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic presence checks (active must be present) - aligns with entity.isValid but defensive for runtime events
         if (entity.getActive() == null) {
             return EvaluationOutcome.fail("Subscriber active flag must be provided", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If subscriber is not active, nothing to notify -- treat as success (no notification expected)
         if (!entity.getActive()) {
             return EvaluationOutcome.success();
         }

         // For active subscribers enforce contact details and supported contact types
         if (entity.getContactDetails() == null || entity.getContactDetails().getUrl() == null || entity.getContactDetails().getUrl().isBlank()) {
             return EvaluationOutcome.fail("Active subscriber must provide contactDetails.url", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String contactType = entity.getContactType();
         if (contactType == null || contactType.isBlank()) {
             return EvaluationOutcome.fail("contactType must be provided for active subscriber", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Supported contact types for notification: webhook or email
         if (!(contactType.equalsIgnoreCase("webhook") || contactType.equalsIgnoreCase("email"))) {
             return EvaluationOutcome.fail("Unsupported contactType for notifications: " + contactType, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // preferredPayload should be either 'full' or 'summary'
         String preferredPayload = entity.getPreferredPayload();
         if (preferredPayload == null || preferredPayload.isBlank()) {
             return EvaluationOutcome.fail("preferredPayload must be provided for active subscriber", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!(preferredPayload.equalsIgnoreCase("full") || preferredPayload.equalsIgnoreCase("summary"))) {
             return EvaluationOutcome.fail("preferredPayload must be either 'full' or 'summary'", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If webhook, ensure URL looks like HTTP/HTTPS (basic quality check)
         if (contactType.equalsIgnoreCase("webhook")) {
             String url = entity.getContactDetails().getUrl();
             if (!(url.startsWith("http://") || url.startsWith("https://"))) {
                 return EvaluationOutcome.fail("webhook URL must use http or https scheme", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

        return EvaluationOutcome.success();
    }
}