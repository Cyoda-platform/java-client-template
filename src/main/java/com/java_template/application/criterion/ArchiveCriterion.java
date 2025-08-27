package com.java_template.application.criterion;

import com.java_template.application.entity.report.version_1.Report;
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
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Report.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
         Report entity = context.entity();

         // Validation: required identity and timestamps
         if (entity.getReportId() == null || entity.getReportId().isBlank()) {
             return EvaluationOutcome.fail("reportId is required for archival", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getGeneratedAt() == null || entity.getGeneratedAt().isBlank()) {
             return EvaluationOutcome.fail("generatedAt is required for archival", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality: ensure report contains rows
         if (entity.getRows() == null || entity.getRows().isEmpty()) {
             return EvaluationOutcome.fail("report has no rows and cannot be archived", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: only exported reports are eligible for archival
         if (entity.getExportedAt() == null || entity.getExportedAt().isBlank()) {
             return EvaluationOutcome.fail("report has not been exported; only exported reports may be archived", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // All checks passed; eligible for archival
         return EvaluationOutcome.success();
    }
}