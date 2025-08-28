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
public class ActiveSubscriberCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ActiveSubscriberCriterion(SerializerFactory serializerFactory) {
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
        // Must match criterion name exactly
        return "ActiveSubscriberCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Subscriber entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Active flag must be true to be considered an active subscriber
         if (entity.getActive() == null || !Boolean.TRUE.equals(entity.getActive())) {
             return EvaluationOutcome.fail("Subscriber is not active", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String deliveryPref = entity.getDeliveryPreference();
         if (deliveryPref == null || deliveryPref.isBlank()) {
             return EvaluationOutcome.fail("Delivery preference is required for active subscriber", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate delivery details according to preference
         if ("webhook".equalsIgnoreCase(deliveryPref)) {
             String url = entity.getWebhookUrl();
             if (url == null || url.isBlank()) {
                 return EvaluationOutcome.fail("Webhook URL must be provided for webhook delivery preference", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             String lower = url.toLowerCase();
             if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                 return EvaluationOutcome.fail("Webhook URL must start with http:// or https://", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else if ("email".equalsIgnoreCase(deliveryPref)) {
             String email = entity.getContactEmail();
             if (email == null || email.isBlank()) {
                 return EvaluationOutcome.fail("Contact email must be provided for email delivery preference", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // basic email sanity check
             if (!email.contains("@") || !email.contains(".")) {
                 return EvaluationOutcome.fail("Contact email appears invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else {
             return EvaluationOutcome.fail("Unsupported delivery preference: " + deliveryPref, StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}