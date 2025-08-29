package com.java_template.application.criterion;

import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
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
            .evaluateEntity(IngestionJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // must match exact criterion name
        return "FetchFailureCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         if (entity == null) {
             logger.debug("IngestionJob entity is null in FetchFailureCriterion");
             return EvaluationOutcome.fail("IngestionJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required field check
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("IngestionJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim().toUpperCase();

         // If the job explicitly failed during fetch step, report as business rule failure
         if ("FAILED".equals(status)) {
             String jobId = entity.getJobId();
             String source = entity.getSourceUrl();
             String message = "IngestionJob fetch failed";
             if (jobId != null && !jobId.isBlank()) {
                 message += " for jobId=" + jobId;
             }
             if (source != null && !source.isBlank()) {
                 message += " (source=" + source + ")";
             }
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If job is marked COMPLETED but there is no lastRunAt timestamp, that's a data quality issue
         if ("COMPLETED".equals(status)) {
             if (entity.getLastRunAt() == null || entity.getLastRunAt().isBlank()) {
                 return EvaluationOutcome.fail("IngestionJob marked COMPLETED but lastRunAt is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             return EvaluationOutcome.success();
         }

         // For PENDING or RUNNING or other statuses, consider as success for this criterion
         return EvaluationOutcome.success();
    }
}