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
public class SubscriberContactValidation implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubscriberContactValidation(SerializerFactory serializerFactory) {
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
         // Validate contactType: must not be null or blank
         if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) {
            return EvaluationOutcome.fail("contactType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         
         // Validate contactValue: must not be null or blank
         if (subscriber.getContactValue() == null || subscriber.getContactValue().isBlank()) {
             return EvaluationOutcome.fail("contactValue is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate active: must not be null
         if (subscriber.getActive() == null) {
             return EvaluationOutcome.fail("active flag must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Additional validation based on contactType
         String contactType = subscriber.getContactType().toLowerCase();
         String contactValue = subscriber.getContactValue();

         switch(contactType) {
             case "email":
                 if (!contactValue.matches("^[^@\s]+@[^@\s]+\\.[^@\s]+$")) {
                     return EvaluationOutcome.fail("Invalid email format", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 break;
             case "webhook":
                 if (!contactValue.matches("https?://.+")) {
                     return EvaluationOutcome.fail("Invalid webhook URL format", StandardEvalReasonCategories.VALIDATION_FAILURE);
                 }
                 break;
             default:
                 return EvaluationOutcome.fail("Unsupported contactType", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
