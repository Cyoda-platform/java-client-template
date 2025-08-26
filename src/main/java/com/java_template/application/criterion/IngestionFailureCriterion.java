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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(BatchJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<BatchJob> context) {
         BatchJob entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("BatchJob entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("BatchJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // This criterion flags actual ingestion/reporting failures. If status is not FAILED, it's not a failure condition.
         if (!"FAILED".equals(status)) {
             return EvaluationOutcome.success();
         }

         // When status is FAILED, we expect a meaningful summary explaining the failure.
         String summary = entity.getSummary();
         if (summary == null || summary.isBlank()) {
             return EvaluationOutcome.fail("BatchJob failed but no summary provided", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Provide the failure reason from summary as a business-rule-level failure indicator.
         return EvaluationOutcome.fail("Ingestion failure: " + summary, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}