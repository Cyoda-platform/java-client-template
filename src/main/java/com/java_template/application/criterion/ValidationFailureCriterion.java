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
public class ValidationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailureCriterion(SerializerFactory serializerFactory) {
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
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         if (entity == null) {
             logger.debug("IngestionJob entity is null");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // sourceEndpoint is required
         String sourceEndpoint = entity.getSourceEndpoint();
         if (sourceEndpoint == null || sourceEndpoint.isBlank()) {
             return EvaluationOutcome.fail("sourceEndpoint is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // basic URL format check
         String seLower = sourceEndpoint.toLowerCase();
         if (!(seLower.startsWith("http://") || seLower.startsWith("https://"))) {
             return EvaluationOutcome.fail("sourceEndpoint must be a valid http(s) URL", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // schedule is required and should resemble a cron-like expression (at least 5 tokens)
         String schedule = entity.getSchedule();
         if (schedule == null || schedule.isBlank()) {
             return EvaluationOutcome.fail("schedule is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String[] schedTokens = schedule.trim().split("\\s+");
         if (schedTokens.length < 5) {
             return EvaluationOutcome.fail("schedule does not appear to be a valid cron expression", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // initiatedBy is required
         String initiatedBy = entity.getInitiatedBy();
         if (initiatedBy == null || initiatedBy.isBlank()) {
             return EvaluationOutcome.fail("initiatedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // processedCount if present must be non-negative (data quality)
         Integer processedCount = entity.getProcessedCount();
         if (processedCount != null && processedCount < 0) {
             return EvaluationOutcome.fail("processedCount must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If status is COMPLETED, finishedAt must be present
         String status = entity.getStatus();
         String finishedAt = entity.getFinishedAt();
         if ("COMPLETED".equalsIgnoreCase(status) && (finishedAt == null || finishedAt.isBlank())) {
             return EvaluationOutcome.fail("finishedAt must be present when status is COMPLETED", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}