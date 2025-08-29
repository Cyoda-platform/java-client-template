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
public class PersistFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public PersistFailureCriterion(SerializerFactory serializerFactory) {
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
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<IngestionJob> context) {
         IngestionJob entity = context.entity();

         if (entity == null) {
             logger.warn("IngestionJob entity is null in PersistFailureCriterion");
             return EvaluationOutcome.fail("Entity missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields validation
         if (entity.getJobId() == null || entity.getJobId().isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: persistence resulted in a FAILED status
         if ("FAILED".equalsIgnoreCase(entity.getStatus())) {
             String msg = String.format("IngestionJob persisted with FAILED status (jobId=%s)", entity.getJobId());
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality: completed jobs must have a lastRunAt timestamp
         if ("COMPLETED".equalsIgnoreCase(entity.getStatus())) {
             if (entity.getLastRunAt() == null || entity.getLastRunAt().isBlank()) {
                 String msg = String.format("Completed IngestionJob missing lastRunAt (jobId=%s)", entity.getJobId());
                 return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // No persist-related failures detected
         return EvaluationOutcome.success();
    }
}