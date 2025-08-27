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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Objects;

@Component
public class KPIsReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private static final String REQUIRED_STATUS = "GENERATING";

    public KPIsReadyCriterion(SerializerFactory serializerFactory) {
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
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Use exact criterion name match (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob reportJob = context.entity();

         if (reportJob == null) {
             logger.warn("ReportJob entity is null in KPIsReadyCriterion");
             return EvaluationOutcome.fail("ReportJob is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: criterion applies when job is in GENERATING status
         String status = reportJob.getStatus();
         if (status == null || !REQUIRED_STATUS.equals(status)) {
             return EvaluationOutcome.fail("ReportJob must be in GENERATING status to evaluate KPI readiness", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate required configuration fields
         if (reportJob.getTemplateId() == null || reportJob.getTemplateId().isBlank()) {
             return EvaluationOutcome.fail("templateId is required to generate attachments", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (reportJob.getOutputFormats() == null || reportJob.getOutputFormats().isBlank()) {
             return EvaluationOutcome.fail("outputFormats is required (e.g., PDF,CSV)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Ensure at least one supported output format is requested and formats are valid tokens
         String formats = reportJob.getOutputFormats();
         boolean supportsPdfOrCsv = Arrays.stream(formats.split(","))
             .map(String::trim)
             .filter(f -> !f.isBlank())
             .anyMatch(f -> f.equalsIgnoreCase("PDF") || f.equalsIgnoreCase("CSV"));
         if (!supportsPdfOrCsv) {
             return EvaluationOutcome.fail("outputFormats must include at least PDF or CSV", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (reportJob.getRecipients() == null || reportJob.getRecipients().isBlank()) {
             return EvaluationOutcome.fail("recipients are required to send the generated report", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic recipients validation: at least one entry contains '@' and a domain part
         boolean validRecipientFound = Arrays.stream(reportJob.getRecipients().split(","))
             .map(String::trim)
             .filter(r -> !r.isBlank())
             .anyMatch(r -> r.contains("@") && r.indexOf('@') > 0 && r.indexOf('.') > r.indexOf('@') );
         if (!validRecipientFound) {
             return EvaluationOutcome.fail("recipients must contain at least one valid email address", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (reportJob.getName() == null || reportJob.getName().isBlank()) {
             return EvaluationOutcome.fail("report name is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Validate period dates
         String periodStart = reportJob.getPeriodStart();
         String periodEnd = reportJob.getPeriodEnd();
         if (periodStart == null || periodStart.isBlank() || periodEnd == null || periodEnd.isBlank()) {
             return EvaluationOutcome.fail("periodStart and periodEnd are required to compute KPIs", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         LocalDate startDate;
         LocalDate endDate;
         try {
             startDate = LocalDate.parse(periodStart);
             endDate = LocalDate.parse(periodEnd);
         } catch (DateTimeParseException ex) {
             logger.warn("Failed to parse period dates: start='{}' end='{}' - {}", periodStart, periodEnd, ex.getMessage());
             return EvaluationOutcome.fail("Invalid periodStart or periodEnd format, expected ISO date (YYYY-MM-DD)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (startDate.isAfter(endDate)) {
             return EvaluationOutcome.fail("periodStart must be earlier than or equal to periodEnd", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Additional sanity: period should not be excessively large (protecting against accidental huge ranges)
         long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
         if (days > 3650) { // arbitrary 10-year guard
             return EvaluationOutcome.fail("period range is too large", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If all checks pass, KPIs are considered ready for attachment generation
         return EvaluationOutcome.success();
    }
}