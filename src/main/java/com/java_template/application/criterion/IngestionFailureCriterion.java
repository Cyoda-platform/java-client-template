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

@Component
public class IngestionFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public IngestionFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(BatchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // MUST use exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BatchJob> context) {
         BatchJob entity = context.entity();

         // Basic validation: required fields must be present
         if (entity.getJobName() == null || entity.getJobName().isBlank()) {
             logger.debug("BatchJob missing jobName");
             return EvaluationOutcome.fail("Job name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getRunMonth() == null || entity.getRunMonth().isBlank()) {
             logger.debug("BatchJob missing runMonth");
             return EvaluationOutcome.fail("Run month is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             logger.debug("BatchJob missing status");
             return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: if the job is marked as FAILED then ingestion failed
         if ("FAILED".equalsIgnoreCase(entity.getStatus())) {
             String summary = entity.getSummary() != null && !entity.getSummary().isBlank()
                 ? entity.getSummary()
                 : "no summary provided";
             String message = "Ingestion failed for job '" + entity.getJobName() + "': " + summary;
             logger.info("IngestionFailureCriterion - failing job {}: {}", entity.getJobName(), summary);
             return EvaluationOutcome.fail(message, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If not explicitly failed, consider success for this criterion
         return EvaluationOutcome.success();
    }
}