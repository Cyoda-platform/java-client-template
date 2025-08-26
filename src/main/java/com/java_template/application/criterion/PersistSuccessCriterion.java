package com.java_template.application.criterion;

import com.java_template.application.entity.job.version_1.Job;
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
public class PersistSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Job.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job entity = context.entity();

         // Basic presence check for state
         if (entity.getState() == null || entity.getState().isBlank()) {
             return EvaluationOutcome.fail("Job state is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String state = entity.getState().trim();

         // Success expected only when state is SUCCEEDED
         if ("SUCCEEDED".equalsIgnoreCase(state)) {
             // finishedAt should be present for a successful job
             if (entity.getFinishedAt() == null || entity.getFinishedAt().isBlank()) {
                 return EvaluationOutcome.fail("finishedAt must be present for a succeeded job", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // No fatal lastError must be present on success
             if (entity.getLastError() != null && !entity.getLastError().isBlank()) {
                 return EvaluationOutcome.fail("lastError present despite succeeded state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // If explicitly failed
         if ("FAILED".equalsIgnoreCase(state)) {
             String err = (entity.getLastError() == null || entity.getLastError().isBlank()) ? "Job marked as FAILED" : ("Job failed: " + entity.getLastError());
             return EvaluationOutcome.fail(err, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Other states are not considered successful for this persistence criterion
         return EvaluationOutcome.fail("Job is not in a terminal succeeded state", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
    }
}