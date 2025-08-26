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
public class IngestionFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IngestionFailureCriterion(SerializerFactory serializerFactory) {
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
         Job job = context.entity();
         if (job == null) {
             logger.warn("Job entity is null in IngestionFailureCriterion");
             return EvaluationOutcome.fail("Job entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If the job explicitly has error details -> consider it a failure condition
         String errorDetails = job.getErrorDetails();
         if (errorDetails != null && !errorDetails.isBlank()) {
             return EvaluationOutcome.fail("Ingestion reported error: " + errorDetails, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate ingestion summary presence and counts
         Job.IngestionSummary summary = job.getIngestionSummary();
         if (summary == null) {
             // Missing summary likely indicates ingestion did not complete successfully
             return EvaluationOutcome.fail("Ingestion summary missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Integer fetched = summary.getRecordsFetched();
         Integer processed = summary.getRecordsProcessed();
         Integer failed = summary.getRecordsFailed();

         // Basic validation of numeric fields
         if (fetched == null || fetched < 0) {
             return EvaluationOutcome.fail("Invalid recordsFetched value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (processed == null || processed < 0) {
             return EvaluationOutcome.fail("Invalid recordsProcessed value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (failed == null || failed < 0) {
             return EvaluationOutcome.fail("Invalid recordsFailed value", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If there are failed records and no successful processing, treat as ingestion failure
         if (failed > 0 && (processed == 0)) {
             return EvaluationOutcome.fail("Ingestion failed: " + failed + " records failed and none processed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If nothing was processed at all, consider it a failure (no useful work done)
         if (processed == 0) {
             return EvaluationOutcome.fail("Ingestion completed with zero records processed", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Otherwise, not a failure
         return EvaluationOutcome.success();
    }
}