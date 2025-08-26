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

import java.time.DateTimeException;
import java.time.Instant;

@Component
public class IngestionSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IngestionSuccessCriterion(SerializerFactory serializerFactory) {
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
             return EvaluationOutcome.fail("BatchJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // status must be COMPLETED for a successful ingestion
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (!"COMPLETED".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("BatchJob status is not COMPLETED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // startedAt and finishedAt must be present
         String startedAt = entity.getStartedAt();
         if (startedAt == null || startedAt.isBlank()) {
             return EvaluationOutcome.fail("startedAt is required for completed jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String finishedAt = entity.getFinishedAt();
         if (finishedAt == null || finishedAt.isBlank()) {
             return EvaluationOutcome.fail("finishedAt is required for completed jobs", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate timestamp semantics: finishedAt must be same or after startedAt
         try {
             Instant startInstant = Instant.parse(startedAt);
             Instant finishInstant = Instant.parse(finishedAt);
             if (finishInstant.isBefore(startInstant)) {
                 return EvaluationOutcome.fail("finishedAt is before startedAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } catch (DateTimeException dte) {
             logger.debug("Timestamp parse error for BatchJob id {}: {}", entity.getId(), dte.getMessage());
             return EvaluationOutcome.fail("Invalid timestamp format for startedAt/finishedAt", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Summary should contain ingestion information (processor records ingestion summary)
         String summary = entity.getSummary();
         if (summary == null || summary.isBlank()) {
             return EvaluationOutcome.fail("summary is missing; ingestion details expected", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}