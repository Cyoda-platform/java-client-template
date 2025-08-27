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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Component
public class AutoArchiveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AutoArchiveCriterion(SerializerFactory serializerFactory) {
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
        // must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
         Report report = context.entity();

         // Basic data quality checks
         if (report == null) {
             return EvaluationOutcome.fail("Report entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (!report.isValid()) {
             return EvaluationOutcome.fail("Report failed basic validation", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (report.getReportId() == null || report.getReportId().isBlank()) {
             return EvaluationOutcome.fail("Missing reportId", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         // Business rule: only exported reports are eligible for auto-archive
         String exportedAt = report.getExportedAt();
         if (exportedAt == null || exportedAt.isBlank()) {
             return EvaluationOutcome.fail("Report not exported; cannot auto-archive", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Parse exportedAt timestamp and ensure it's a valid ISO timestamp.
         // Accept both OffsetDateTime and LocalDateTime representations (assume system zone if no offset).
         OffsetDateTime exportedDateTime;
         try {
             exportedDateTime = OffsetDateTime.parse(exportedAt);
         } catch (DateTimeParseException e) {
             try {
                 LocalDateTime ldt = LocalDateTime.parse(exportedAt);
                 exportedDateTime = ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
                 logger.debug("Parsed exportedAt as LocalDateTime and converted to OffsetDateTime using system zone for report {}",
                         report.getReportId());
             } catch (DateTimeParseException e2) {
                 logger.warn("Invalid exportedAt timestamp for report {}: {}", report.getReportId(), exportedAt, e2);
                 return EvaluationOutcome.fail("Invalid exportedAt timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Business rule: auto-archive only if exportedAt is older than threshold (30 days)
         OffsetDateTime now = OffsetDateTime.now(exportedDateTime.getOffset());
         Duration age = Duration.between(exportedDateTime, now);
         long daysOld = age.toDays();

         final long AUTO_ARCHIVE_DAYS = 30L;
         if (daysOld < AUTO_ARCHIVE_DAYS) {
             return EvaluationOutcome.fail(
                 String.format("Report exported %d days ago; must be at least %d days old to auto-archive", daysOld, AUTO_ARCHIVE_DAYS),
                 StandardEvalReasonCategories.BUSINESS_RULE_FAILURE
             );
         }

         // Additional sanity: report must contain rows to archive (reports without rows shouldn't be archived)
         if (report.getRows() == null || report.getRows().isEmpty()) {
             return EvaluationOutcome.fail("Report has no rows; skipping auto-archive", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // All checks passed — eligible for auto-archive
         logger.info("Report {} eligible for auto-archive (exportedAt={}, daysOld={})",
                 report.getReportId(), exportedDateTime, daysOld);
         return EvaluationOutcome.success();
    }
}