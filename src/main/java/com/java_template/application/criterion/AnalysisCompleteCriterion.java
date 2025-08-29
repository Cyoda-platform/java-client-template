package com.java_template.application.criterion;

import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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
public class AnalysisCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AnalysisCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(WeeklyReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<WeeklyReport> context) {
         WeeklyReport entity = context.entity();
         if (entity == null) {
             logger.warn("WeeklyReport entity is null in AnalysisCompleteCriterion");
             return EvaluationOutcome.fail("WeeklyReport entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required business identifiers and timing
         if (entity.getReportId() == null || entity.getReportId().isBlank()) {
             return EvaluationOutcome.fail("reportId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getWeekStart() == null || entity.getWeekStart().isBlank()) {
             return EvaluationOutcome.fail("weekStart is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
             // generatedAt indicates analysis run completed and timestamped
             return EvaluationOutcome.fail("generatedAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Analysis output quality checks
         if (entity.getSummary() == null || entity.getSummary().isBlank()) {
             return EvaluationOutcome.fail("analysis summary is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: template application should only proceed if report is in GENERATING state.
         // Fail if status indicates failure or is not in the expected state.
         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String normalized = status.trim().toUpperCase();
         if ("FAILED".equals(normalized)) {
             return EvaluationOutcome.fail("report generation has failed; cannot apply template", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
         if (!"GENERATING".equals(normalized) && !"READY".equals(normalized) && !"DISPATCHED".equals(normalized)) {
             // Allow progressing if already READY/DISPATCHED, or expected state GENERATING.
             return EvaluationOutcome.fail("report is not in a state suitable for template application", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}