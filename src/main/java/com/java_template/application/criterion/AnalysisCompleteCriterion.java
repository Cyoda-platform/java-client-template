package com.java_template.application.criterion;

import com.java_template.application.entity.analysisjob.version_1.AnalysisJob;
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
public class AnalysisCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AnalysisCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AnalysisJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AnalysisJob> context) {
         AnalysisJob entity = context.entity();
         // Validate basic presence of status
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("AnalysisJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim();

         // If job is RUNNING we expect an actual analysis completion timestamp to mark analysis done
         if (status.equalsIgnoreCase("RUNNING")) {
             if (entity.getStartedAt() == null || entity.getStartedAt().isBlank()) {
                 return EvaluationOutcome.fail("AnalysisJob.startedAt must be set for running jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             // completedAt indicates analysis processing finished and job ready for report generation
             if (entity.getCompletedAt() == null || entity.getCompletedAt().isBlank()) {
                 return EvaluationOutcome.fail("Analysis processing not finished yet (completedAt missing)", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // If already in GENERATING_REPORT or COMPLETED consider criterion satisfied
         if (status.equalsIgnoreCase("GENERATING_REPORT") || status.equalsIgnoreCase("COMPLETED")) {
             return EvaluationOutcome.success();
         }

         // Other statuses are not eligible for transitioning to report generation
         return EvaluationOutcome.fail(
             "AnalysisJob is not in a state eligible for report generation: status=" + status,
             StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
         );
    }
}