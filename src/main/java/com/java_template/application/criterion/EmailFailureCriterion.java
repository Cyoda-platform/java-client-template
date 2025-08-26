package com.java_template.application.criterion;

import com.java_template.application.entity.analysisreport.version_1.AnalysisReport;
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
public class EmailFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(AnalysisReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AnalysisReport> context) {
         AnalysisReport report = context.entity();
         if (report == null) {
             logger.warn("EmailFailureCriterion: received null AnalysisReport entity");
             return EvaluationOutcome.fail("AnalysisReport entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Basic validation of essential fields
         if (report.getReportId() == null || report.getReportId().isBlank()) {
             return EvaluationOutcome.fail("reportId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (report.getRecipientEmail() == null || report.getRecipientEmail().isBlank()) {
             return EvaluationOutcome.fail("recipientEmail is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (report.getStatus() == null || report.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Business rule: if the analysis report status indicates a send failure, mark criterion as failed
         if ("FAILED".equalsIgnoreCase(report.getStatus())) {
             String msg = String.format("Email delivery failed for report %s to recipient %s",
                     report.getReportId(), report.getRecipientEmail());
             logger.info("EmailFailureCriterion triggered: {}", msg);
             return EvaluationOutcome.fail(msg, StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Otherwise, consider it a success (no email failure)
         return EvaluationOutcome.success();
    }
}