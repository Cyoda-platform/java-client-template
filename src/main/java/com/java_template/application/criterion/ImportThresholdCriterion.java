package com.java_template.application.criterion;

import com.java_template.application.entity.pet_ingestion_job.version_1.PetIngestionJob;
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

import java.util.List;

@Component
public class ImportThresholdCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Threshold for maximum allowed imports in a single job. Tunable.
    private static final int MAX_IMPORT_THRESHOLD = 10000;

    public ImportThresholdCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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
             return EvaluationOutcome.fail("Job entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields check
         if (job.getJobId() == null || job.getJobId().trim().isEmpty()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSource() == null || job.getSource().trim().isEmpty()) {
             return EvaluationOutcome.fail("source is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getRequestedBy() == null || job.getRequestedBy().trim().isEmpty()) {
             return EvaluationOutcome.fail("requestedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getStatus() == null || job.getStatus().trim().isEmpty()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality checks for counts and errors
         Integer importedCount = job.getImportedCount();
         List<String> errors = job.getErrors();

         if (importedCount != null && importedCount < 0) {
             return EvaluationOutcome.fail("importedCount must not be negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if ("completed".equalsIgnoreCase(job.getStatus()) && (importedCount == null)) {
             return EvaluationOutcome.fail("completed job must have importedCount set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (errors != null && importedCount != null && errors.size() > importedCount) {
             return EvaluationOutcome.fail("number of reported errors exceeds importedCount", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: protect against excessively large imports in a single job
         if (importedCount != null && importedCount > MAX_IMPORT_THRESHOLD) {
             return EvaluationOutcome.fail(
                 String.format("importedCount (%d) exceeds maximum allowed threshold of %d", importedCount, MAX_IMPORT_THRESHOLD),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }

         return EvaluationOutcome.success();
    }
}