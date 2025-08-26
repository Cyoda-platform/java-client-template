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
public class JobCompletionEventCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobCompletionEventCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name match
        return "JobCompletionEventCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Job> context) {
         Job job = context.entity();
         if (job == null) {
             logger.warn("Job entity is null in JobCompletionEventCriterion");
             return EvaluationOutcome.fail("Job entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be present
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion is intended to validate completed jobs that have moved to notification dispatch stage
         if (!"NOTIFIED_SUBSCRIBERS".equals(job.getStatus())) {
             return EvaluationOutcome.fail("Job must be in NOTIFIED_SUBSCRIBERS state to run completion checks", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // finishedAt must be present for a completed job
         if (job.getFinishedAt() == null || job.getFinishedAt().isBlank()) {
             return EvaluationOutcome.fail("Job.finishedAt is required for completed jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Notification policy is required so that notifications can be created/dispatched
         Job.NotificationPolicy np = job.getNotificationPolicy();
         if (np == null || np.getType() == null || np.getType().isBlank()) {
             return EvaluationOutcome.fail("notificationPolicy.type is required to dispatch notifications", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ingestion summary must be present and consistent
         Job.IngestionSummary summary = job.getIngestionSummary();
         if (summary == null) {
             return EvaluationOutcome.fail("ingestionSummary is required for completed jobs", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         Integer fetched = summary.getRecordsFetched();
         Integer processed = summary.getRecordsProcessed();
         Integer failed = summary.getRecordsFailed();

         if (fetched == null || fetched < 0) {
             return EvaluationOutcome.fail("ingestionSummary.recordsFetched must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (processed == null || processed < 0) {
             return EvaluationOutcome.fail("ingestionSummary.recordsProcessed must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (failed == null || failed < 0) {
             return EvaluationOutcome.fail("ingestionSummary.recordsFailed must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic consistency: processed + failed should not exceed fetched
         long sum = (long) processed + (long) failed;
         if (sum > fetched) {
             return EvaluationOutcome.fail("ingestionSummary recordsProcessed + recordsFailed cannot exceed recordsFetched", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}