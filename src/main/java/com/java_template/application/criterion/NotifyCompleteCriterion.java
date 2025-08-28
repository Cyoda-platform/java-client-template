package com.java_template.application.criterion;

import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
public class NotifyCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public NotifyCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Requirement: supports() method MUST use exact criterion name (case-sensitive)
        if (modelSpec == null || modelSpec.operationName() == null) {
            return false;
        }
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob entity = context.entity();

         // Basic required fields must be present
         if (entity.getJobId() == null || entity.getJobId().isBlank()) {
             return EvaluationOutcome.fail("jobId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getDataSourceUrl() == null || entity.getDataSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("dataSourceUrl is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // For NotifyCompleteCriterion we expect the report artifact to be generated
         if (entity.getReportLocation() == null || entity.getReportLocation().isBlank()) {
             return EvaluationOutcome.fail("Report location is missing - report must be generated before completing notifications",
                     StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
             return EvaluationOutcome.fail("generatedAt timestamp is missing - report must have generation time",
                     StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: this criterion should be evaluated when job is in NOTIFYING state.
         String status = entity.getStatus();
         if (status == null || !status.equals("NOTIFYING")) {
             return EvaluationOutcome.fail("Job is not in NOTIFYING state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Optional: ensure notifyFilters present (if system requires subscriber selection)
         if (entity.getNotifyFilters() == null || entity.getNotifyFilters().isBlank()) {
             // treat missing filters as data quality issue but allow completion only if explicit default behavior exists.
             // Since we cannot inspect notification outcomes from ReportJob, mark as data quality failure.
             return EvaluationOutcome.fail("notifyFilters missing - unable to determine recipients", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}