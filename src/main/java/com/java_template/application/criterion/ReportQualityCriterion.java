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
public class ReportQualityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportQualityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking report quality criteria for request: {}", request.getId());
        
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
        Report entity = context.entity();
        logger.info("Validating report quality for: {}", entity.getReportName());

        // Check report completeness
        if (!checkReportCompleteness(entity)) {
            return EvaluationOutcome.fail("Report is incomplete", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Check data accuracy
        if (!checkDataAccuracy(entity)) {
            return EvaluationOutcome.fail("Report data accuracy issues", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check report format
        if (!checkReportFormat(entity)) {
            return EvaluationOutcome.fail("Report format issues", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // All quality checks passed
        logger.info("Report quality validation passed for: {}", entity.getReportName());
        return EvaluationOutcome.success();
    }

    private boolean checkReportCompleteness(Report report) {
        // Check if report file exists at specified filePath
        if (report.getFilePath() == null || report.getFilePath().trim().isEmpty()) {
            logger.error("Report file path is not set");
            return false;
        }

        // In a real implementation, this would check if the file actually exists
        // For now, simulate file existence check
        logger.info("Report file path is set: {}", report.getFilePath());

        // Check if all required sections are present
        if (report.getSummary() == null || report.getSummary().trim().isEmpty()) {
            logger.error("Report summary is missing");
            return false;
        }

        if (report.getTotalProducts() == null || report.getTotalProducts() < 0) {
            logger.error("Total products count is invalid: {}", report.getTotalProducts());
            return false;
        }

        if (report.getTopPerformingProducts() == null) {
            logger.error("Top performing products list is missing");
            return false;
        }

        if (report.getUnderperformingProducts() == null) {
            logger.error("Underperforming products list is missing");
            return false;
        }

        if (report.getKeyInsights() == null) {
            logger.error("Key insights list is missing");
            return false;
        }

        logger.info("Report completeness check passed");
        return true;
    }

    private boolean checkDataAccuracy(Report report) {
        // Check for missing or null values in critical sections
        if (report.getReportPeriodStart() == null || report.getReportPeriodEnd() == null) {
            logger.error("Report period dates are missing");
            return false;
        }

        if (report.getGenerationDate() == null) {
            logger.error("Report generation date is missing");
            return false;
        }

        // Validate that period start is before or equal to period end
        if (report.getReportPeriodStart().isAfter(report.getReportPeriodEnd())) {
            logger.error("Report period start date is after end date");
            return false;
        }

        // Check that total products count makes sense with the lists
        int expectedMinProducts = 0;
        if (report.getTopPerformingProducts() != null) {
            expectedMinProducts += report.getTopPerformingProducts().size();
        }
        if (report.getUnderperformingProducts() != null) {
            expectedMinProducts += report.getUnderperformingProducts().size();
        }

        if (report.getTotalProducts() != null && report.getTotalProducts() < expectedMinProducts) {
            logger.warn("Total products count ({}) seems inconsistent with product lists ({})", 
                       report.getTotalProducts(), expectedMinProducts);
        }

        logger.info("Report data accuracy check passed");
        return true;
    }

    private boolean checkReportFormat(Report report) {
        // Check file format is supported
        if (report.getFileFormat() == null || report.getFileFormat().trim().isEmpty()) {
            logger.error("Report file format is not specified");
            return false;
        }

        String format = report.getFileFormat().toUpperCase();
        if (!"PDF".equals(format) && !"HTML".equals(format) && !"CSV".equals(format)) {
            logger.error("Unsupported report file format: {}", report.getFileFormat());
            return false;
        }

        // In a real implementation, this would:
        // 1. Check if PDF is properly formatted and readable
        // 2. Verify all images and charts render correctly
        // 3. Ensure text is not truncated or corrupted
        // 4. Confirm file is not password protected
        
        // For now, simulate format validation
        logger.info("Report format validation passed for format: {}", format);
        return true;
    }
}
