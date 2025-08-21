package com.java_template.application.criterion;

import com.java_template.application.entity.monthlyreport.version_1.MonthlyReport;
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

import java.time.Instant;
import java.time.YearMonth;

@Component
public class ArchiveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ArchiveCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(MonthlyReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<MonthlyReport> context) {
        MonthlyReport report = context.entity();
        if (report == null) return EvaluationOutcome.fail("Report missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        try {
            if (report.getGeneratedAt() == null || report.getGeneratedAt().isBlank()) {
                return EvaluationOutcome.fail("Report has no generatedAt", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
            YearMonth gen = YearMonth.parse(report.getMonth());
            YearMonth now = YearMonth.now();
            // Archive if older than 36 months
            if (gen.plusMonths(36).isBefore(now)) {
                return EvaluationOutcome.success();
            }
            return EvaluationOutcome.fail("Report not old enough for archive", StandardEvalReasonCategories.VALIDATION_FAILURE);
        } catch (Exception ex) {
            logger.error("Error during ArchiveCriterion", ex);
            return EvaluationOutcome.fail("Archive check failed: " + ex.getMessage(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
