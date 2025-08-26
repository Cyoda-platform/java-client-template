package com.java_template.application.criterion;

import com.java_template.application.entity.batchjob.version_1.BatchJob;
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
public class JobReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(BatchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BatchJob> context) {
         BatchJob entity = context.entity();
         if (entity == null) {
             logger.warn("JobReadyCriterion: received null entity in evaluation context");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Required: jobName
         if (entity.getJobName() == null || entity.getJobName().isBlank()) {
             return EvaluationOutcome.fail("jobName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: runMonth in format YYYY-MM
         String runMonth = entity.getRunMonth();
         if (runMonth == null || runMonth.isBlank()) {
             return EvaluationOutcome.fail("runMonth is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String runMonthPattern = "^\\d{4}-\\d{2}$";
         if (!runMonth.matches(runMonthPattern)) {
             return EvaluationOutcome.fail("runMonth must be in format YYYY-MM", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: createdAt
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: scheduleCron (basic non-empty check)
         if (entity.getScheduleCron() == null || entity.getScheduleCron().isBlank()) {
             return EvaluationOutcome.fail("scheduleCron is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be VALIDATING to transition to RUNNING (business rule)
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (!"VALIDATING".equals(status)) {
             // Not ready to start ingestion unless job is in VALIDATING state
             return EvaluationOutcome.fail("Job is not in VALIDATING state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed -> job is ready
         return EvaluationOutcome.success();
    }
}