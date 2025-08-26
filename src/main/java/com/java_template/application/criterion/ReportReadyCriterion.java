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

import java.util.List;

@Component
public class ReportReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(MonthlyReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Use exact criterion name (case-sensitive) as required by critical rules
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<MonthlyReport> context) {
         MonthlyReport report = context.entity();

         if (report == null) {
             return EvaluationOutcome.fail("MonthlyReport entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business-state check: only proceed if report is in a state that indicates it was generated and pending publish.
         String status = report.getPublishedStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("publishedStatus is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         boolean acceptableState = status.equalsIgnoreCase("GENERATED")
                 || status.equalsIgnoreCase("PENDING_PUBLISH")
                 || status.equalsIgnoreCase("PENDING");
         if (!acceptableState) {
             return EvaluationOutcome.fail("Report is not in a generated/pending state for publishing", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Required artifacts for publishing
         if (report.getReportFileRef() == null || report.getReportFileRef().isBlank()) {
             return EvaluationOutcome.fail("reportFileRef is required to publish the report", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         List<String> recipients = report.getAdminRecipients();
         if (recipients == null || recipients.isEmpty()) {
             return EvaluationOutcome.fail("At least one admin recipient is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         for (String r : recipients) {
             if (r == null || r.isBlank()) {
                 return EvaluationOutcome.fail("adminRecipients contains an invalid email entry", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
         }

         // Basic data quality checks
         if (report.getTotalUsers() == null || report.getTotalUsers() < 0) {
             return EvaluationOutcome.fail("totalUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (report.getNewUsers() == null || report.getNewUsers() < 0) {
             return EvaluationOutcome.fail("newUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (report.getInvalidRecordsCount() == null || report.getInvalidRecordsCount() < 0) {
             return EvaluationOutcome.fail("invalidRecordsCount must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         if (report.getUpdatedUsers() == null || report.getUpdatedUsers() < 0) {
             return EvaluationOutcome.fail("updatedUsers must be present and non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Sample records quality
         List<MonthlyReport.SampleRecord> samples = report.getSampleRecords();
         if (samples == null || samples.isEmpty()) {
             return EvaluationOutcome.fail("At least one sample record is required for verification", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         for (MonthlyReport.SampleRecord sr : samples) {
             if (sr == null) {
                 return EvaluationOutcome.fail("sampleRecords contains a null entry", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (sr.getId() == null) {
                 return EvaluationOutcome.fail("sampleRecords entries must have an id", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (sr.getName() == null || sr.getName().isBlank()) {
                 return EvaluationOutcome.fail("sampleRecords entries must have a name", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             if (sr.getProcessingStatus() == null || sr.getProcessingStatus().isBlank()) {
                 return EvaluationOutcome.fail("sampleRecords entries must have a processingStatus", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         // Operational guard: avoid publishing if delivery attempts already exceed a threshold
         Integer attempts = report.getDeliveryAttempts();
         if (attempts != null && attempts > 5) {
             return EvaluationOutcome.fail("Delivery attempts exceeded threshold; manual intervention required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Timestamp presence
         if (report.getGeneratedAt() == null || report.getGeneratedAt().isBlank()) {
             return EvaluationOutcome.fail("generatedAt timestamp is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Month presence
         if (report.getMonth() == null || report.getMonth().isBlank()) {
             return EvaluationOutcome.fail("month is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}