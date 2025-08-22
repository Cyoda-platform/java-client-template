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
public class ImportErrorCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ImportErrorCriterion(SerializerFactory serializerFactory) {
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
         PetIngestionJob entity = context.entity();

         // Required fields
         if (entity.getJobId() == null || entity.getJobId().trim().isEmpty()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().trim().isEmpty()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim().toLowerCase();

         // If the job failed, ensure errors are present and report as data quality failure.
         if ("failed".equals(status)) {
             if (entity.getErrors() == null || entity.getErrors().isEmpty()) {
                 // A failed job without error details is a data quality issue.
                 return EvaluationOutcome.fail("Import job marked as failed but contains no error details",
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             String details = String.join(", ", entity.getErrors());
             return EvaluationOutcome.fail("Import job failed: " + details, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If completed but with non-empty errors, treat as data quality failure (partial failures).
         if ("completed".equals(status) && entity.getErrors() != null && !entity.getErrors().isEmpty()) {
             String details = String.join(", ", entity.getErrors());
             return EvaluationOutcome.fail("Import job completed with errors: " + details,
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Running jobs should have a startedAt timestamp
         if ("running".equals(status) && (entity.getStartedAt() == null || entity.getStartedAt().trim().isEmpty())) {
             return EvaluationOutcome.fail("Running import job is missing startedAt timestamp",
                 StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If importedCount is negative, it's a data quality issue
         if (entity.getImportedCount() != null && entity.getImportedCount() < 0) {
             return EvaluationOutcome.fail("importedCount cannot be negative",
                 StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}