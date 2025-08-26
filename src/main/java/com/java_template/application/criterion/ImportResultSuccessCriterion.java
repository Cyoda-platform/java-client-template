package com.java_template.application.criterion;

import com.java_template.application.entity.importjob.version_1.ImportJob;
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
public class ImportResultSuccessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ImportResultSuccessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ImportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ImportJob> context) {
         ImportJob entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("ImportJob entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("ImportJob.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         ImportJob.ResultSummary summary = entity.getResultSummary();
         if (summary == null) {
             return EvaluationOutcome.fail("ImportJob.resultSummary is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Integer failed = summary.getFailed();
         if (failed == null) {
             return EvaluationOutcome.fail("ImportJob.resultSummary.failed must be present", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // If job completed, success only when there are no failed rows
         if ("Completed".equalsIgnoreCase(status)) {
             if (failed == 0) {
                 return EvaluationOutcome.success();
             } else {
                 return EvaluationOutcome.fail(
                     "Import completed with failed rows: " + failed,
                     StandardEvalReasonCategories.DATA_QUALITY_FAILURE
                 );
             }
         }

         // Explicit failed state => business rule failure
         if ("Failed".equalsIgnoreCase(status)) {
             return EvaluationOutcome.fail("Import job marked as Failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // For other states (Pending, Running, etc.) it's not yet successful
         return EvaluationOutcome.fail("Import job not completed (status=" + status + ")", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}