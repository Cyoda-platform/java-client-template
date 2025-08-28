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
public class FetchCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public FetchCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(ReportJob.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob entity = context.entity();

         // Basic required fields
         if (entity.getJobId() == null || entity.getJobId().isBlank()) {
             return EvaluationOutcome.fail("job_id is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getDataSourceUrl() == null || entity.getDataSourceUrl().isBlank()) {
             return EvaluationOutcome.fail("data_source_url is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: fetch completion must occur while job is in FETCHING state
         if (!"FETCHING".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("job must be in FETCHING state for fetch completion", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Data quality check: ensure requested metrics is present (otherwise nothing to analyze)
         if (entity.getRequestedMetrics() == null || entity.getRequestedMetrics().isBlank()) {
             return EvaluationOutcome.fail("requested_metrics must be specified for downstream analysis", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Warning-level check: reportLocation or generatedAt should not already be set at fetch completion.
         if (entity.getReportLocation() != null && !entity.getReportLocation().isBlank()) {
             // attach as a warning via reason attachment strategy; still mark success here
             logger.warn("ReportJob {} has reportLocation populated at fetch completion", entity.getJobId());
         }
         if (entity.getGeneratedAt() != null && !entity.getGeneratedAt().isBlank()) {
             logger.warn("ReportJob {} has generatedAt populated at fetch completion", entity.getJobId());
         }

         return EvaluationOutcome.success();
    }
}