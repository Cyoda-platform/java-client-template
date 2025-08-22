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
         PetIngestionJob job = context.entity();
         if (job == null) {
             return EvaluationOutcome.fail("PetIngestionJob entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.trim().isEmpty()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If job is marked as failed, there must be error details captured
         if ("failed".equalsIgnoreCase(status)) {
             if (job.getErrors() == null || job.getErrors().isEmpty()) {
                 return EvaluationOutcome.fail("Ingestion job marked as 'failed' but no errors recorded", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // report failure with number of errors
             int errorCount = job.getErrors() == null ? 0 : job.getErrors().size();
             return EvaluationOutcome.fail("Ingestion job failed with " + errorCount + " error(s)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If job completed successfully, importedCount should be positive
         if ("completed".equalsIgnoreCase(status)) {
             Integer imported = job.getImportedCount();
             if (imported == null || imported <= 0) {
                 return EvaluationOutcome.fail("Completed ingestion job has no imported records", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Running jobs must have a startedAt timestamp
         if ("running".equalsIgnoreCase(status)) {
             if (job.getStartedAt() == null || job.getStartedAt().trim().isEmpty()) {
                 return EvaluationOutcome.fail("Running ingestion job missing startedAt timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}