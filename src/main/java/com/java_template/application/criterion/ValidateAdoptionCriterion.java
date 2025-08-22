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
public class ValidateAdoptionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidateAdoptionCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("AdoptionJob entity missing in evaluation context");
             return EvaluationOutcome.fail("Adoption job payload is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required identifiers
         if (job.getPetId() == null || job.getPetId().isBlank()) {
             return EvaluationOutcome.fail("petId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getUserId() == null || job.getUserId().isBlank()) {
             return EvaluationOutcome.fail("userId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Request type must be adoption
         if (job.getRequestType() == null || job.getRequestType().isBlank()) {
             return EvaluationOutcome.fail("requestType is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"adoption".equals(job.getRequestType())) {
             return EvaluationOutcome.fail("requestType must be 'adoption'", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Job must be in the correct lifecycle state for validation (initial state = pending)
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"pending".equals(job.getStatus())) {
             return EvaluationOutcome.fail("adoption job must be in 'pending' status for validation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Fee sanity check (also enforced by entity.isValid(), but validate defensively here)
         if (job.getFee() == null) {
             return EvaluationOutcome.fail("fee must be provided (use 0.0 if no fee)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getFee() < 0.0) {
             return EvaluationOutcome.fail("fee must be non-negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // At this stage we validated the adoption job payload and basic lifecycle/business constraints.
         // Existence checks for Pet and User, and pet/user-status checks, are expected to be performed by processors
         // that have access to the current Pet and User entities. This criterion ensures the job is well-formed and
         // in the correct state to proceed with those checks.
        return EvaluationOutcome.success();
    }
}