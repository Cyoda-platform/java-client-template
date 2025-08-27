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

import java.time.DateTimeException;
import java.time.Instant;

@Component
public class ExportCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ExportCompleteCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // CRITICAL: use exact criterion name match
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
         Report report = context.entity();

         if (report == null) {
             logger.debug("ExportCompleteCriterion: null entity in context");
             return EvaluationOutcome.fail("Report entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String reportId = report.getReportId();
         // exportedAt must be present for an export to be considered complete
         if (report.getExportedAt() == null || report.getExportedAt().isBlank()) {
             logger.debug("ExportCompleteCriterion: report {} missing exportedAt", reportId);
             return EvaluationOutcome.fail("Report has not been exported (exportedAt missing)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // report should contain rows (an exported report with no rows is a data quality issue)
         if (report.getRows() == null || report.getRows().isEmpty()) {
             logger.debug("ExportCompleteCriterion: report {} exportedAt present but rows are empty or null", reportId);
             return EvaluationOutcome.fail("Exported report contains no rows", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // basic entity validity check (ensures reportId, generatedAt and row contents are present/valid)
         if (!report.isValid()) {
             logger.debug("ExportCompleteCriterion: report {} failed basic validation checks", reportId);
             return EvaluationOutcome.fail("Report failed validation checks", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate exportedAt formatting and logical relation to generatedAt when possible
         String exportedAt = report.getExportedAt();
         String generatedAt = report.getGeneratedAt();
         try {
             Instant exportedInstant = Instant.parse(exportedAt);
             if (generatedAt != null && !generatedAt.isBlank()) {
                 try {
                     Instant generatedInstant = Instant.parse(generatedAt);
                     if (exportedInstant.isBefore(generatedInstant)) {
                         logger.debug("ExportCompleteCriterion: report {} exportedAt ({}) is before generatedAt ({})", reportId, exportedAt, generatedAt);
                         return EvaluationOutcome.fail("Export timestamp is before report generation time", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                     }
                 } catch (DateTimeException dtEx) {
                     // If generatedAt can't be parsed, log but do not fail validation solely for that reason
                     logger.debug("ExportCompleteCriterion: report {} has unparsable generatedAt '{}': {}", reportId, generatedAt, dtEx.getMessage());
                 }
             }
         } catch (DateTimeException ex) {
             logger.debug("ExportCompleteCriterion: report {} has unparsable exportedAt '{}': {}", reportId, exportedAt, ex.getMessage());
             return EvaluationOutcome.fail("exportedAt timestamp is not a valid ISO-8601 instant", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}