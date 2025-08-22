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
public class FetchSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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

         // Validate required input: petSource
         if (entity.getPetSource() == null || entity.getPetSource().isBlank()) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             logger.warn("FetchSuccessCriterion: petSource missing for job {}", id);
             return EvaluationOutcome.fail("petSource is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // fetchedCount must be present and non-negative
         Integer fetched = entity.getFetchedCount();
         if (fetched == null) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             logger.warn("FetchSuccessCriterion: fetchedCount is null for job {}", id);
             return EvaluationOutcome.fail("fetchedCount is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (fetched < 0) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             logger.warn("FetchSuccessCriterion: fetchedCount is negative ({}) for job {}", fetched, id);
             return EvaluationOutcome.fail("fetchedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If job has explicit FAILED status -> fail
         String status = entity.getStatus();
         if (status != null && status.equalsIgnoreCase("FAILED")) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             logger.info("FetchSuccessCriterion: job {} has status FAILED", id);
             return EvaluationOutcome.fail("Job status is FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If errors were recorded during fetch, treat as failure for success criterion
         if (entity.getErrors() != null && !entity.getErrors().isEmpty()) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             String firstError = entity.getErrors().get(0);
             logger.info("FetchSuccessCriterion: job {} contains errors from fetch: {}", id, firstError);
             return EvaluationOutcome.fail("Errors present from fetch: " + firstError, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If nothing was fetched -> not a success
         if (fetched == 0) {
             String id = entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>";
             logger.info("FetchSuccessCriterion: job {} fetched 0 items", id);
             return EvaluationOutcome.fail("No items fetched", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed -> consider fetch successful
         logger.debug("FetchSuccessCriterion: job {} passed fetch-success checks (fetchedCount={})",
                 entity.getJobId() != null ? entity.getJobId() : "<unknown-jobId>", fetched);
        return EvaluationOutcome.success();
    }
}