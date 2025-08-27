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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ExportRequestedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ExportRequestedCriterion(SerializerFactory serializerFactory) {
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
        // Must use exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportJob> context) {
         ReportJob job = context.entity();

         if (job == null) {
             return EvaluationOutcome.fail("ReportJob entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required identifying/requester fields for an export request
         if (job.getRequestedBy() == null || job.getRequestedBy().isBlank()) {
             return EvaluationOutcome.fail("requestedBy is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (job.getCreatedAt() == null || job.getCreatedAt().isBlank()) {
             return EvaluationOutcome.fail("createdAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Status must be present
         if (job.getStatus() == null || job.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Export should only be considered for completed jobs
         if (!"COMPLETED".equalsIgnoreCase(job.getStatus())) {
             return EvaluationOutcome.fail("ReportJob status must be COMPLETED to request export", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // exportFormats must be present and non-empty to indicate an export was requested
         List<String> exportFormats = job.getExportFormats();
         if (exportFormats == null || exportFormats.isEmpty()) {
             return EvaluationOutcome.fail("No export formats requested", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate allowed formats (business-defined allowed set: CSV, PDF)
         Set<String> allowed = Set.of("CSV", "PDF");
         List<String> invalid = exportFormats.stream()
             .filter(f -> f == null || f.isBlank() || !allowed.contains(f.toUpperCase()))
             .collect(Collectors.toList());
         if (!invalid.isEmpty()) {
             return EvaluationOutcome.fail("Invalid export formats requested: " + invalid, StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // If notify is provided it must not be blank
         if (job.getNotify() != null && job.getNotify().isBlank()) {
             return EvaluationOutcome.fail("notify is present but blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}