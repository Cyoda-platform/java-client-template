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
 * GenerationFailureCriterion
 * 
 * Checks if report generation failed.
 * Used in Report workflow transition: generation_failed
 */
@Component
public class GenerationFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public GenerationFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking report generation failure criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Report.class, this::validateGenerationFailure)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic to check if report generation failed
     */
    private EvaluationOutcome validateGenerationFailure(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
        Report report = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (report == null) {
            logger.warn("Report is null");
            return EvaluationOutcome.fail("Report entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if summary is null or empty, which indicates generation failure
        if (report.getSummary() == null || report.getSummary().trim().isEmpty()) {
            logger.warn("Report generation failed for Report: {} - missing summary", report.getReportId());
            return EvaluationOutcome.fail("Report generation failed - no summary generated", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if report format is missing
        if (report.getReportFormat() == null || report.getReportFormat().trim().isEmpty()) {
            logger.warn("Report generation failed for Report: {} - missing format", report.getReportId());
            return EvaluationOutcome.fail("Report generation failed - no format specified", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if analysis results are missing (should be present for successful generation)
        if (report.getAnalysisResults() == null || report.getAnalysisResults().isEmpty()) {
            logger.warn("Report generation failed for Report: {} - missing analysis results", report.getReportId());
            return EvaluationOutcome.fail("Report generation failed - no analysis results available", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Check if generated timestamp is missing
        if (report.getGeneratedAt() == null) {
            logger.warn("Report generation failed for Report: {} - missing generation timestamp", report.getReportId());
            return EvaluationOutcome.fail("Report generation failed - no generation timestamp", 
                                        StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // If we reach here, generation was successful
        logger.debug("Report generation was successful for Report: {}", report.getReportId());
        return EvaluationOutcome.success();
    }
}
