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

/**
 * ReportPermissionCriterion - Validates report generation permissions
 * Checks if user has permission to generate reports
 */
@Component
public class ReportPermissionCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportPermissionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Report permission criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Report.class, this::validateReportPermission)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for report generation permissions
     */
    private EvaluationOutcome validateReportPermission(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
        Report report = context.entityWithMetadata().entity();

        // Check if report is null (structural validation)
        if (report == null) {
            logger.warn("Report is null");
            return EvaluationOutcome.fail("Report is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check basic entity validity
        if (!report.isValid()) {
            logger.warn("Report is not valid: {}", report.getReportName());
            return EvaluationOutcome.fail("Report is not valid", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check if generator email is provided
        if (report.getGeneratedBy() == null || report.getGeneratedBy().trim().isEmpty()) {
            logger.warn("No generator specified for report: {}", report.getReportName());
            return EvaluationOutcome.fail("Report generator must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check generator email format
        if (!isValidEmailFormat(report.getGeneratedBy())) {
            logger.warn("Invalid generator email format: {}", report.getGeneratedBy());
            return EvaluationOutcome.fail("Invalid generator email format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check report name is provided
        if (report.getReportName() == null || report.getReportName().trim().isEmpty()) {
            logger.warn("No report name specified");
            return EvaluationOutcome.fail("Report name must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check report type is valid
        if (!isValidReportType(report.getReportType())) {
            logger.warn("Invalid report type: {}", report.getReportType());
            return EvaluationOutcome.fail("Invalid report type", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check format is valid
        if (!isValidFormat(report.getFormat())) {
            logger.warn("Invalid report format: {}", report.getFormat());
            return EvaluationOutcome.fail("Invalid report format", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check data range is provided and valid
        if (report.getDataRange() == null || report.getDataRange().trim().isEmpty()) {
            logger.warn("No data range specified for report: {}", report.getReportName());
            return EvaluationOutcome.fail("Data range must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (!isValidDataRange(report.getDataRange())) {
            logger.warn("Invalid data range format: {}", report.getDataRange());
            return EvaluationOutcome.fail("Invalid data range format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check parameters are provided
        if (report.getParameters() == null || report.getParameters().trim().isEmpty()) {
            logger.warn("No parameters specified for report: {}", report.getReportName());
            return EvaluationOutcome.fail("Report parameters must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check parameters are valid JSON
        if (!isValidJson(report.getParameters())) {
            logger.warn("Invalid JSON parameters for report: {}", report.getReportName());
            return EvaluationOutcome.fail("Report parameters must be valid JSON", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Validate data range is not too large (max 2 years)
        if (!isReasonableDataRange(report.getDataRange())) {
            logger.warn("Data range too large for report: {} ({})", report.getReportName(), report.getDataRange());
            return EvaluationOutcome.fail("Data range cannot exceed 2 years", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    /**
     * Validates email format
     */
    private boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return email.contains("@") && email.contains(".") && 
               email.indexOf("@") > 0 && 
               email.indexOf("@") < email.lastIndexOf(".");
    }

    /**
     * Validates if the report type is one of the allowed values
     */
    private boolean isValidReportType(String type) {
        return "SUBMISSION_STATUS".equals(type) || 
               "USER_ACTIVITY".equals(type) || 
               "PERFORMANCE_METRICS".equals(type);
    }

    /**
     * Validates if the format is one of the allowed values
     */
    private boolean isValidFormat(String format) {
        return "PDF".equals(format) || 
               "CSV".equals(format) || 
               "JSON".equals(format);
    }

    /**
     * Validates data range format (simplified - should be "YYYY-MM-DD,YYYY-MM-DD")
     */
    private boolean isValidDataRange(String dataRange) {
        if (dataRange == null || !dataRange.contains(",")) {
            return false;
        }
        
        String[] parts = dataRange.split(",");
        if (parts.length != 2) {
            return false;
        }
        
        // Basic date format validation (YYYY-MM-DD)
        return parts[0].trim().matches("\\d{4}-\\d{2}-\\d{2}") && 
               parts[1].trim().matches("\\d{4}-\\d{2}-\\d{2}");
    }

    /**
     * Check if data range is reasonable (not more than 2 years)
     */
    private boolean isReasonableDataRange(String dataRange) {
        if (!isValidDataRange(dataRange)) {
            return false;
        }
        
        try {
            String[] parts = dataRange.split(",");
            String startDate = parts[0].trim();
            String endDate = parts[1].trim();
            
            int startYear = Integer.parseInt(startDate.substring(0, 4));
            int endYear = Integer.parseInt(endDate.substring(0, 4));
            
            return (endYear - startYear) <= 2;
        } catch (Exception e) {
            logger.warn("Error validating data range: {}", dataRange, e);
            return false;
        }
    }

    /**
     * Simple JSON validation
     */
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        json = json.trim();
        return (json.startsWith("{") && json.endsWith("}")) || 
               (json.startsWith("[") && json.endsWith("]"));
    }
}
