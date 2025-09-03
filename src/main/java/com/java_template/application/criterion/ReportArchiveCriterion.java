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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class ReportArchiveCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReportArchiveCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.info("Checking report archive criteria for request: {}", request.getId());
        
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
        logger.info("Checking archive criteria for report: {}", entity.getReportName());

        LocalDateTime now = LocalDateTime.now();

        // Check report age - reports distributed for > 90 days should be archived
        if (entity.getGenerationDate() != null) {
            long daysSinceGeneration = ChronoUnit.DAYS.between(entity.getGenerationDate(), now);
            if (daysSinceGeneration > 90) {
                logger.info("Report {} has been distributed for {} days, eligible for archival", 
                           entity.getReportName(), daysSinceGeneration);
                return EvaluationOutcome.success();
            }
        }

        // Check if report is not the most recent report of its type
        if (isSupersededByNewerReport(entity)) {
            logger.info("Report {} has been superseded by newer version, eligible for archival", 
                       entity.getReportName());
            return EvaluationOutcome.success();
        }

        // Check storage constraints (simulated)
        if (shouldArchiveForStorageConstraints(entity)) {
            logger.info("Report {} should be archived due to storage constraints", 
                       entity.getReportName());
            return EvaluationOutcome.success();
        }

        // Report should be retained
        logger.info("Report {} should be retained", entity.getReportName());
        return EvaluationOutcome.fail("Report should be retained", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }

    private boolean isSupersededByNewerReport(Report report) {
        // In a real implementation, this would:
        // 1. Query for newer reports of the same type
        // 2. Check if there are reports with later generation dates
        // 3. Verify the newer reports cover the same or overlapping periods
        
        // For simulation, assume reports older than 30 days are superseded
        if (report.getGenerationDate() != null) {
            long daysSinceGeneration = ChronoUnit.DAYS.between(report.getGenerationDate(), LocalDateTime.now());
            return daysSinceGeneration > 30;
        }
        
        return false;
    }

    private boolean shouldArchiveForStorageConstraints(Report report) {
        // In a real implementation, this would:
        // 1. Check total report storage usage
        // 2. Compare against storage thresholds
        // 3. Check if report is flagged for retention
        // 4. Verify report is not referenced by active analysis
        
        // For simulation, assume some reports need archiving for storage
        // Use report name hash to simulate storage pressure
        return Math.abs(report.getReportName().hashCode()) % 20 == 0; // 5% of reports
    }
}
