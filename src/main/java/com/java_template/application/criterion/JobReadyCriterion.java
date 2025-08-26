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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(BatchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BatchJob> context) {
         BatchJob entity = context.entity();
         if (entity == null) {
             logger.warn("BatchJob entity is null in JobReadyCriterion");
             return EvaluationOutcome.fail("BatchJob entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
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
             return EvaluationOutcome.fail("runMonth must have format YYYY-MM", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: scheduleCron must be present (ValidateJobParamsProcessor enforces this)
         if (entity.getScheduleCron() == null || entity.getScheduleCron().isBlank()) {
             return EvaluationOutcome.fail("scheduleCron is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required: createdAt must be present
         if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: job must currently be in VALIDATING state to be eligible to move to RUNNING
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"VALIDATING".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("job must be in VALIDATING state to start running", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Additional safety: ensure job has not already started
         if (entity.getStartedAt() != null && !entity.getStartedAt().isBlank()) {
             return EvaluationOutcome.fail("job has already been started", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}