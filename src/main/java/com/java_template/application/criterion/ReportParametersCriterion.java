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
 * ReportParametersCriterion - Validates report parameters and configuration
 * Checks if report parameters are valid for completion
 */
@Component
public class ReportParametersCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportParametersCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking Report parameters criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Report.class, this::validateReportParameters)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for report parameters
     */
    private EvaluationOutcome validateReportParameters(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
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

        // Check parameters are valid JSON
        if (!isValidJson(report.getParameters())) {
            logger.warn("Invalid JSON parameters for report: {}", report.getReportName());
            return EvaluationOutcome.fail("Report parameters must be valid JSON", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check data range is valid and reasonable
        if (!isValidDataRange(report.getDataRange())) {
            logger.warn("Invalid data range for report: {} ({})", report.getReportName(), report.getDataRange());
            return EvaluationOutcome.fail("Invalid data range format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (!isReasonableDataRange(report.getDataRange())) {
            logger.warn("Data range too large for report: {} ({})", report.getReportName(), report.getDataRange());
            return EvaluationOutcome.fail("Data range cannot exceed 2 years", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check file path is set and valid
        if (report.getFilePath() == null || report.getFilePath().trim().isEmpty()) {
            logger.warn("No file path specified for report: {}", report.getReportName());
            return EvaluationOutcome.fail("File path must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        if (!isValidFilePath(report.getFilePath())) {
            logger.warn("Invalid file path for report: {} ({})", report.getReportName(), report.getFilePath());
            return EvaluationOutcome.fail("Invalid file path format", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check generation date is set
        if (report.getGenerationDate() == null) {
            logger.warn("No generation date specified for report: {}", report.getReportName());
            return EvaluationOutcome.fail("Generation date must be specified", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate report type specific parameters
        if (!validateReportTypeSpecificParameters(report)) {
            logger.warn("Invalid parameters for report type: {} ({})", report.getReportType(), report.getReportName());
            return EvaluationOutcome.fail("Invalid parameters for this report type", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate format compatibility
        if (!isFormatCompatible(report.getReportType(), report.getFormat())) {
            logger.warn("Incompatible format {} for report type: {}", report.getFormat(), report.getReportType());
            return EvaluationOutcome.fail("Format not compatible with report type", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        return EvaluationOutcome.success();
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
            
            // Check end date is not before start date
            if (endDate.compareTo(startDate) < 0) {
                return false;
            }
            
            return (endYear - startYear) <= 2;
        } catch (Exception e) {
            logger.warn("Error validating data range: {}", dataRange, e);
            return false;
        }
    }

    /**
     * Validate file path format
     */
    private boolean isValidFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        
        // Should start with / and contain reasonable path structure
        return filePath.startsWith("/") && filePath.length() <= 500 && 
               !filePath.contains("..") && filePath.contains(".");
    }

    /**
     * Validate report type specific parameters
     */
    private boolean validateReportTypeSpecificParameters(Report report) {
        String reportType = report.getReportType();
        String parameters = report.getParameters();
        
        // Basic validation - parameters should be non-empty JSON
        if (!isValidJson(parameters) || "{}".equals(parameters.trim())) {
            // Empty parameters might be acceptable for some report types
            return "SUBMISSION_STATUS".equals(reportType) || "USER_ACTIVITY".equals(reportType);
        }
        
        // For PERFORMANCE_METRICS, we might need specific parameters
        if ("PERFORMANCE_METRICS".equals(reportType)) {
            // Could validate specific parameters here
            return parameters.length() > 2; // More than just "{}"
        }
        
        return true;
    }

    /**
     * Check if format is compatible with report type
     */
    private boolean isFormatCompatible(String reportType, String format) {
        // All formats are generally compatible with all report types
        // But we could add specific restrictions here
        
        if ("PERFORMANCE_METRICS".equals(reportType)) {
            // Performance metrics might work better with CSV or JSON
            return "CSV".equals(format) || "JSON".equals(format) || "PDF".equals(format);
        }
        
        if ("USER_ACTIVITY".equals(reportType)) {
            // User activity reports might work better with CSV
            return "CSV".equals(format) || "PDF".equals(format);
        }
        
        if ("SUBMISSION_STATUS".equals(reportType)) {
            // Submission status reports work with all formats
            return "PDF".equals(format) || "CSV".equals(format) || "JSON".equals(format);
        }
        
        return true;
    }
}
