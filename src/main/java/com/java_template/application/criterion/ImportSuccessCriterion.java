package com.java_template.application.criterion;

import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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
public class ImportSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ImportSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetIngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetIngestionJob> context) {
         PetIngestionJob job = context.entity();
         if (job == null) {
             logger.warn("PetIngestionJob entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getJobId() == null || job.getJobId().trim().isEmpty()) {
             logger.warn("PetIngestionJob missing jobId");
             return EvaluationOutcome.fail("Job id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getStatus() == null || job.getStatus().trim().isEmpty()) {
             logger.warn("PetIngestionJob {} missing status", job.getJobId());
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus().trim().toLowerCase();

         switch (status) {
             case "completed":
                 // completed should have completedAt and no errors
                 if (job.getCompletedAt() == null || job.getCompletedAt().trim().isEmpty()) {
                     logger.warn("PetIngestionJob {} marked completed but missing completedAt", job.getJobId());
                     return EvaluationOutcome.fail("completedAt is required for completed jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (job.getImportedCount() == null) {
                     logger.warn("PetIngestionJob {} completed but importedCount is missing", job.getJobId());
                     return EvaluationOutcome.fail("importedCount is required for completed jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 if (job.getErrors() != null && !job.getErrors().isEmpty()) {
                     logger.warn("PetIngestionJob {} completed with errors: {}", job.getJobId(), job.getErrors());
                     return EvaluationOutcome.fail("Job completed but errors were recorded", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
                 return EvaluationOutcome.success();

             case "failed":
                 logger.info("PetIngestionJob {} has failed status", job.getJobId());
                 return EvaluationOutcome.fail("Ingestion job failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);

             case "running":
             case "pending":
                 logger.info("PetIngestionJob {} not completed yet (status={})", job.getJobId(), job.getStatus());
                 return EvaluationOutcome.fail("Ingestion job not completed", StandardEvalReasonCategories.VALIDATION_FAILURE);

             default:
                 logger.warn("PetIngestionJob {} has unknown status '{}'", job.getJobId(), job.getStatus());
                 return EvaluationOutcome.fail("Unknown job status: " + job.getStatus(), StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
    }
}