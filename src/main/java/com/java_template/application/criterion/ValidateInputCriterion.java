package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.lookupjob.version_1.LookupJob;
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
public class ValidateInputCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateInputCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(LookupJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<LookupJob> context) {
         LookupJob job = context.entity();
         if (job == null) {
             return EvaluationOutcome.fail("LookupJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer userId = job.getUserId();
         if (userId == null) {
             logger.debug("ValidateInputCriterion: userId is null for job={}", job.getTechnicalId());
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (userId <= 0) {
             logger.debug("ValidateInputCriterion: userId is not positive for job={} userId={}", job.getTechnicalId(), userId);
             return EvaluationOutcome.fail("userId must be a positive integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}
