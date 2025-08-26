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
public class RetryExhaustedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Default maximum attempts before considering retries exhausted.
    private static final int MAX_ATTEMPTS = 3;

    public RetryExhaustedCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name
        return "RetryExhaustedCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             logger.warn("Job entity is null in RetryExhaustedCriterion");
             return EvaluationOutcome.fail("Job entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer attempts = job.getAttempts();
         if (attempts == null) {
             logger.debug("Job {} has null attempts", job.getId());
             return EvaluationOutcome.fail("Job attempts not set", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (attempts < 0) {
             logger.debug("Job {} has negative attempts: {}", job.getId(), attempts);
             return EvaluationOutcome.fail("Job attempts invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (attempts >= MAX_ATTEMPTS) {
             String msg = String.format("Retry attempts exhausted (%d attempts)", attempts);
             logger.info("Job {}: {}", job.getId(), msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}