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
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ReportReadinessCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final ObjectMapper objectMapper;

    public ReportReadinessCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Validating Report readiness for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Report.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
        Report report = context.entity();
        
        // Validate report ID
        if (report.getReportId() == null || report.getReportId().trim().isEmpty()) {
            return EvaluationOutcome.fail("Report ID is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate generation timestamp
        if (report.getGeneratedAt() == null) {
            return EvaluationOutcome.fail("Report generation timestamp is required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate metrics
        if (report.getTotalBooksAnalyzed() == null || report.getTotalBooksAnalyzed() <= 0) {
            return EvaluationOutcome.fail("Total books analyzed must be greater than 0", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (report.getTotalPageCount() == null || report.getTotalPageCount() <= 0) {
            return EvaluationOutcome.fail("Total page count must be greater than 0", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (report.getAveragePageCount() == null || report.getAveragePageCount() <= 0) {
            return EvaluationOutcome.fail("Average page count must be greater than 0", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate JSON content
        if (report.getPopularTitles() == null || report.getPopularTitles().length() < 10) {
            return EvaluationOutcome.fail("Popular titles data is required and must be substantial", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (!isValidJson(report.getPopularTitles())) {
            return EvaluationOutcome.fail("Popular titles must be valid JSON", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (report.getPublicationDateInsights() == null || report.getPublicationDateInsights().length() < 10) {
            return EvaluationOutcome.fail("Publication date insights are required and must be substantial", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (!isValidJson(report.getPublicationDateInsights())) {
            return EvaluationOutcome.fail("Publication date insights must be valid JSON", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate summary
        if (report.getReportSummary() == null || report.getReportSummary().length() < 50) {
            return EvaluationOutcome.fail("Report summary is required and must be at least 50 characters", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Validate email recipients
        if (report.getEmailRecipients() == null || !report.getEmailRecipients().contains("@")) {
            return EvaluationOutcome.fail("Valid email recipients are required", 
                StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        logger.info("Report readiness validation passed for report: {}", report.getReportId());
        return EvaluationOutcome.success();
    }

    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            logger.warn("Invalid JSON detected: {}", e.getMessage());
            return false;
        }
    }
}
