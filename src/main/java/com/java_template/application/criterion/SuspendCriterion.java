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
public class SuspendCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SuspendCriterion(SerializerFactory serializerFactory) {
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
        return "SuspendCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
         Subscriber entity = context.entity();
         if (entity == null) {
             logger.debug("SuspendCriterion: subscriber entity is null");
             return EvaluationOutcome.fail("Subscriber entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getId() == null || entity.getId().isBlank()) {
             logger.debug("SuspendCriterion: subscriber id missing");
             return EvaluationOutcome.fail("Subscriber id is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getActive() == null) {
             logger.debug("SuspendCriterion: active flag missing for subscriber {}", entity.getId());
             return EvaluationOutcome.fail("Subscriber active flag is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Only allow suspension when subscriber is currently active
         if (!entity.getActive()) {
             logger.debug("SuspendCriterion: subscriber {} is already inactive", entity.getId());
             return EvaluationOutcome.fail("Subscriber already suspended", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Ensure contact details are present (contact URL is required by Subscriber contract)
         if (entity.getContactDetails() == null || entity.getContactDetails().getUrl() == null || entity.getContactDetails().getUrl().isBlank()) {
             logger.debug("SuspendCriterion: subscriber {} has missing contact URL", entity.getId());
             return EvaluationOutcome.fail("Subscriber contact URL is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}