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
public class SubscriberValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SubscriberValidationCriterion(SerializerFactory serializerFactory) {
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
         // Validate subscriberId presence
         if (subscriber.getSubscriberId() == null || subscriber.getSubscriberId().isBlank()) {
            return EvaluationOutcome.fail("subscriberId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Validate contactType presence
         if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) {
            return EvaluationOutcome.fail("contactType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Validate contactAddress presence
         if (subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank()) {
            return EvaluationOutcome.fail("contactAddress is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Validate active flag presence
         if (subscriber.getActive() == null) {
            return EvaluationOutcome.fail("active flag must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // Business rule: active subscriber must have a valid contactAddress
         if (subscriber.getActive() && (subscriber.getContactAddress() == null || subscriber.getContactAddress().isBlank())) {
             return EvaluationOutcome.fail("Active subscriber must have a valid contactAddress", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         return EvaluationOutcome.success();
    }
}
