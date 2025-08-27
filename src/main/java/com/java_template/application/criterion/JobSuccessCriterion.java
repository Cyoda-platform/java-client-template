package com.java_template.application.criterion;

import com.java_template.application.entity.weeklyjob.version_1.WeeklyJob;
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
public class JobSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public JobSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(WeeklyJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklyJob> context) {
         WeeklyJob entity = context.entity();

         if (entity == null) {
             logger.warn("WeeklyJob entity is null in JobSuccessCriterion");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Basic required fields for a successful run
         if (entity.getName() == null || entity.getName().isBlank()) {
             return EvaluationOutcome.fail("Job name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getRecipients() == null || entity.getRecipients().isEmpty()) {
             return EvaluationOutcome.fail("No recipients configured for job", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         // ensure recipients are non-blank
         for (String r : entity.getRecipients()) {
             if (r == null || r.isBlank()) {
                 return EvaluationOutcome.fail("One or more recipients are invalid", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         if (entity.getApiEndpoint() == null || entity.getApiEndpoint().isBlank()) {
             return EvaluationOutcome.fail("apiEndpoint is required for ingestion", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Job must be marked COMPLETED to be considered a success
         if (entity.getStatus() == null || !entity.getStatus().equalsIgnoreCase("COMPLETED")) {
             return EvaluationOutcome.fail("Job status is not COMPLETED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Timestamps from the last run are expected when job completed successfully
         if (entity.getLastRunAt() == null || entity.getLastRunAt().isBlank()) {
             return EvaluationOutcome.fail("lastRunAt timestamp missing after completion", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (entity.getNextRunAt() == null || entity.getNextRunAt().isBlank()) {
             return EvaluationOutcome.fail("nextRunAt not scheduled after completion", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}