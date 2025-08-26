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
public class RetryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final int MAX_ATTEMPTS = 5;

    public RetryCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
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
             logger.warn("RetryCriterion: job entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Integer attempts = job.getAttempts();
         if (attempts == null) {
             logger.warn("RetryCriterion: attempts is null for job id={}", job.getId());
             return EvaluationOutcome.fail("Job attempts not set", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (attempts < 0) {
             logger.warn("RetryCriterion: attempts negative for job id={}", job.getId());
             return EvaluationOutcome.fail("Job attempts is negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String status = job.getStatus();
         if (status == null || status.isBlank()) {
             logger.warn("RetryCriterion: status missing for job id={}", job.getId());
             return EvaluationOutcome.fail("Job status is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Criterion applies only when job has failed; otherwise nothing to do
         if (!"failed".equalsIgnoreCase(status)) {
             return EvaluationOutcome.success();
         }

         // Business rule: only retry if attempts < MAX_ATTEMPTS
         if (attempts >= MAX_ATTEMPTS) {
             logger.info("RetryCriterion: max attempts reached for job id={}, attempts={}", job.getId(), attempts);
             return EvaluationOutcome.fail("Max retry attempts reached", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Eligible for retry
         return EvaluationOutcome.success();
    }
}