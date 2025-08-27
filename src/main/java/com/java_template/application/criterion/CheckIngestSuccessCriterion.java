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
public class CheckIngestSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public CheckIngestSuccessCriterion(SerializerFactory serializerFactory) {
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
             logger.warn("CheckIngestSuccessCriterion: job entity is null");
             return EvaluationOutcome.fail("Job entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getJobId() == null || job.getJobId().isBlank()) {
             logger.warn("CheckIngestSuccessCriterion: jobId missing for job");
             return EvaluationOutcome.fail("Job.jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (job.getStatus() == null || job.getStatus().isBlank()) {
             logger.warn("CheckIngestSuccessCriterion: status missing for job {}", job.getJobId());
             return EvaluationOutcome.fail("Job.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion applies when the job is in INGESTING state
         if (!"INGESTING".equalsIgnoreCase(job.getStatus())) {
             logger.info("CheckIngestSuccessCriterion: job {} not in INGESTING state (status={}), skipping success check",
                 job.getJobId(), job.getStatus());
             return EvaluationOutcome.fail("Job is not in INGESTING state", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Job.IngestResult res = job.getIngestResult();
         if (res == null) {
             logger.warn("CheckIngestSuccessCriterion: ingestResult missing for job {}", job.getJobId());
             return EvaluationOutcome.fail("ingestResult is required to determine success", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (res.getErrors() != null && !res.getErrors().isEmpty()) {
             int errorCount = res.getErrors().size();
             logger.info("CheckIngestSuccessCriterion: job {} reported {} ingest errors", job.getJobId(), errorCount);
             return EvaluationOutcome.fail("Ingest reported " + errorCount + " error(s)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Ensure at least one of the counts is present (0 is valid)
         if (res.getCountAdded() == null && res.getCountUpdated() == null) {
             logger.warn("CheckIngestSuccessCriterion: ingest result counts missing for job {}", job.getJobId());
             return EvaluationOutcome.fail("Ingest result counts are missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Counts should be non-negative if present (additional safety)
         if ((res.getCountAdded() != null && res.getCountAdded() < 0) ||
             (res.getCountUpdated() != null && res.getCountUpdated() < 0)) {
             logger.warn("CheckIngestSuccessCriterion: negative ingest counts for job {}", job.getJobId());
             return EvaluationOutcome.fail("Ingest result contains negative counts", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         logger.info("CheckIngestSuccessCriterion: job {} evaluated as SUCCEEDED (added={}, updated={})",
             job.getJobId(), res.getCountAdded(), res.getCountUpdated());
        return EvaluationOutcome.success();
    }
}