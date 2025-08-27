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
public class ActivateCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ActivateCriterion(SerializerFactory serializerFactory) {
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
        // Must match the exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber subscriber = context.entity();

         if (subscriber == null) {
             return EvaluationOutcome.fail("Subscriber entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifiers and metadata
         if (subscriber.getId() == null || subscriber.getId().isBlank()) {
             return EvaluationOutcome.fail("Subscriber id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getCreatedAt() == null || subscriber.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Contact information must be present and addressable
         if (subscriber.getContactType() == null || subscriber.getContactType().isBlank()) {
             return EvaluationOutcome.fail("contactType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (subscriber.getContactDetails() == null || subscriber.getContactDetails().getUrl() == null
             || subscriber.getContactDetails().getUrl().isBlank()) {
             return EvaluationOutcome.fail("contactDetails.url is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: only verified subscribers can be activated
         if (subscriber.getVerified() == null || !subscriber.getVerified()) {
             return EvaluationOutcome.fail("Subscriber must be verified before activation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality check: active flag should not be null (system expects boolean)
         if (subscriber.getActive() == null) {
             return EvaluationOutcome.fail("active flag must be present", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass, allow activation
         return EvaluationOutcome.success();
    }
}