package com.java_template.application.criterion;

import com.java_template.application.entity.petenrichmentjob.version_1.PetEnrichmentJob;
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
public class FetchFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetEnrichmentJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetEnrichmentJob> context) {
         PetEnrichmentJob entity = context.entity();

         // If the job status explicitly indicates failure, mark as failure.
         if (entity.getStatus() != null && entity.getStatus().equalsIgnoreCase("FAILED")) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             logger.debug("PetEnrichmentJob {} marked FAILED by status field.", id);
             return EvaluationOutcome.fail("Fetch failed (status=FAILED) for job " + id,
                     StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If the fetch produced errors, consider it a data-quality failure.
         if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             // Attach a short summary of errors (first one) to reason message to avoid huge payloads.
             String firstError = entity.getErrors().get(0);
             return EvaluationOutcome.fail("Fetch produced errors for job " + id + ": " + firstError,
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If no records were fetched, consider this a data quality issue (nothing to create).
         if (entity.getFetchedCount() != null && entity.getFetchedCount() <= 0) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             return EvaluationOutcome.fail("No records fetched for job " + id,
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Otherwise, not a fetch failure.
         return EvaluationOutcome.success();
    }
}