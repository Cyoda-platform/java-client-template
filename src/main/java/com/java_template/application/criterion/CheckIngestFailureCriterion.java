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
public class CheckIngestFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckIngestFailureCriterion(SerializerFactory serializerFactory) {
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
        // MUST use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();

         if (job == null) {
             logger.debug("CheckIngestFailureCriterion: job entity is null - nothing to evaluate");
             return EvaluationOutcome.success();
         }

         // If job status explicitly indicates failure, mark as failed business rule
         String status = job.getStatus();
         if (status != null && status.equalsIgnoreCase("FAILED")) {
             logger.info("CheckIngestFailureCriterion: job {} status indicates FAILED", job.getJobId());
             return EvaluationOutcome.fail("Job status is FAILED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // If ingestResult reports errors, mark as data quality failure
         Job.IngestResult ingestResult = job.getIngestResult();
         if (ingestResult != null && ingestResult.getErrors() != null && !ingestResult.getErrors().isEmpty()) {
             int errCount = ingestResult.getErrors().size();
             String firstError = ingestResult.getErrors().get(0);
             String msg = "Ingest reported " + errCount + " error(s)";
             if (firstError != null && !firstError.isBlank()) {
                 msg += ". First error: \"" + firstError + "\"";
             }
             logger.info("CheckIngestFailureCriterion: job {} ingest reported errors: {}", job.getJobId(), msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // No failure conditions met
         logger.debug("CheckIngestFailureCriterion: no failure conditions met for job {}", job.getJobId());
         return EvaluationOutcome.success();
    }
}