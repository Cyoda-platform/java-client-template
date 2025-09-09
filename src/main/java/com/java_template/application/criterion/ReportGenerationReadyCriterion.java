package com.java_template.application.criterion;

import com.java_template.application.entity.report_entity.version_1.ReportEntity;
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

import java.time.LocalDateTime;

/**
 * ReportGenerationReadyCriterion - Check if system is ready to generate report
 * Transition: start_generation (scheduled → generating)
 */
@Component
public class ReportGenerationReadyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportGenerationReadyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking ReportGenerationReady criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(ReportEntity.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Check if system is ready to generate report including data availability and system resources
     */
    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<ReportEntity> context) {
        ReportEntity entity = context.entityWithMetadata().entity();

        // Check if entity is null
        if (entity == null) {
            logger.warn("ReportEntity is null");
            return EvaluationOutcome.fail("Report entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if report period is present
        if (entity.getReportPeriod() == null) {
            logger.warn("Report period is null for report: {}", entity.getReportId());
            return EvaluationOutcome.fail("Report period is required", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if report period dates are present
        if (entity.getReportPeriod().getStartDate() == null || entity.getReportPeriod().getEndDate() == null) {
            logger.warn("Report period dates are null for report: {}", entity.getReportId());
            return EvaluationOutcome.fail("Report period start and end dates are required", 
                    StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Check if report period is valid
        if (entity.getReportPeriod().getEndDate().isBefore(entity.getReportPeriod().getStartDate()) ||
            entity.getReportPeriod().getEndDate().equals(entity.getReportPeriod().getStartDate())) {
            logger.warn("Invalid report period for report {}: start={}, end={}", 
                    entity.getReportId(), entity.getReportPeriod().getStartDate(), entity.getReportPeriod().getEndDate());
            return EvaluationOutcome.fail("Report period end date must be after start date", 
                    StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Simplified check for sufficient data - in real implementation, this would query for orders
        // For now, we'll assume there's always sufficient data if the period is valid
        logger.debug("Assuming sufficient data exists for report period");

        // Check system resources (simplified check based on time)
        LocalDateTime currentTime = LocalDateTime.now();
        int currentHour = currentTime.getHour();

        // Prefer report generation during low-traffic hours (2 AM to 6 AM)
        if (currentHour >= 2 && currentHour <= 6) {
            logger.debug("Report generation ready - optimal time: {}:00", currentHour);
            return EvaluationOutcome.success();
        }

        // Allow generation during business hours if urgent
        if ("URGENT".equals(entity.getReportType())) {
            logger.debug("Report generation ready - urgent report type");
            return EvaluationOutcome.success();
        }

        // For regular reports, prefer low-traffic hours
        logger.debug("Report generation not optimal - current hour: {}:00, prefer 2-6 AM", currentHour);
        return EvaluationOutcome.fail("Report generation preferred during low-traffic hours (2-6 AM)", 
                StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
