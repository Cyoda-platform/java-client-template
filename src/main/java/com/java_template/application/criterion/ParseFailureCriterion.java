package com.java_template.application.criterion;

import com.java_template.application.entity.petsyncjob.version_1.PetSyncJob;
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

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

@Component
public class ParseFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ParseFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(PetSyncJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        return "ParseFailureCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<PetSyncJob> context) {
         PetSyncJob job = context.entity();

         if (job == null) {
             return EvaluationOutcome.fail("PetSyncJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic required fields validation (use existing getters only)
         if (job.getId() == null || job.getId().isBlank()) {
             return EvaluationOutcome.fail("Job id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getSource() == null || job.getSource().isBlank()) {
             return EvaluationOutcome.fail("Job source is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Job status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getStartTime() == null || job.getStartTime().isBlank()) {
             return EvaluationOutcome.fail("Job startTime is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate timestamp formats and ordering if endTime present
         String start = job.getStartTime();
         String end = job.getEndTime();
         if (end != null && !end.isBlank()) {
             try {
                 Instant startInstant = Instant.parse(start);
                 Instant endInstant = Instant.parse(end);
                 if (endInstant.isBefore(startInstant)) {
                     return EvaluationOutcome.fail("endTime is before startTime", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             } catch (DateTimeException ex) {
                 logger.debug("Timestamp parse error for job {}: {}", job.getId(), ex.getMessage());
                 return EvaluationOutcome.fail("Invalid ISO timestamp in startTime or endTime", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Detect explicit failure state related to parsing
         String status = job.getStatus().trim();
         String error = job.getErrorMessage();

         if ("failed".equalsIgnoreCase(status)) {
             if (error != null && !error.isBlank()) {
                 // If job failed and an error message exists, consider it a data quality failure (parsing failed)
                 return EvaluationOutcome.fail("Job failed: " + error, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             } else {
                 return EvaluationOutcome.fail("Job failed with no error message", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // If status indicates parsing stage but there is an error message recorded, flag as parse failure
         if ("parsing".equalsIgnoreCase(status) || "parsing_failed".equalsIgnoreCase(status)) {
             if (error != null && !error.isBlank()) {
                 return EvaluationOutcome.fail("Parsing reported errors: " + error, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // If parsing stage but fetchedCount is present and zero, likely nothing parsed
             Integer fetched = job.getFetchedCount();
             if (fetched != null && fetched == 0) {
                 return EvaluationOutcome.fail("Parsing completed but no items parsed (fetched_count=0)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Inconsistent fetchedCount values
         Integer fetchedCount = job.getFetchedCount();
         if (fetchedCount != null && fetchedCount < 0) {
             return EvaluationOutcome.fail("fetchedCount must not be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If there is an error message while overall status is completed/succeeded, flag as data quality issue
         if (( "completed".equalsIgnoreCase(status) || "completed_success".equalsIgnoreCase(status) || "completed_ok".equalsIgnoreCase(status) )
                 && error != null && !error.isBlank()) {
             return EvaluationOutcome.fail("Job marked completed but contains error message: " + error, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // No parse-related failures detected
         return EvaluationOutcome.success();
    }
}