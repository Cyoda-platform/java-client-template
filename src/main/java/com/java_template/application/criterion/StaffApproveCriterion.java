package com.java_template.application.criterion;

import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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
public class StaffApproveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public StaffApproveCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AdoptionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AdoptionJob> context) {
         AdoptionJob job = context.entity();

         if (job == null) {
             return EvaluationOutcome.fail("AdoptionJob entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // requestType must be adoption for this approval flow
         if (job.getRequestType() == null || job.getRequestType().isBlank()) {
             return EvaluationOutcome.fail("requestType is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (!"adoption".equalsIgnoreCase(job.getRequestType())) {
             return EvaluationOutcome.fail("Only adoption requestType can be approved by staff", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // status must be present and in expected state for approval
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         String status = job.getStatus().toLowerCase();
         if ("approved".equals(status)) {
             return EvaluationOutcome.fail("Job is already approved", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (!"review".equals(status)) {
             return EvaluationOutcome.fail("Job must be in 'review' status to be approved", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // decisionBy (staff id) must be provided when approving
         if (job.getDecisionBy() == null || job.getDecisionBy().isBlank()) {
             return EvaluationOutcome.fail("decisionBy (staff id) is required to record approval", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic data quality checks
         if (job.getRequestedAt() == null || job.getRequestedAt().isBlank()) {
             return EvaluationOutcome.fail("requestedAt is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (job.getFee() == null) {
             return EvaluationOutcome.fail("fee must be provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (job.getFee() < 0.0) {
             return EvaluationOutcome.fail("fee must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}