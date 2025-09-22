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
 * SendFailureCriterion
 * 
 * Checks if email sending failed.
 * Used in Report workflow transition: send_failed
 */
@Component
public class SendFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public SendFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking email send failure criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Report.class, this::validateSendFailure)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic to check if email sending failed
     */
    private EvaluationOutcome validateSendFailure(CriterionSerializer.CriterionEntityEvaluationContext<Report> context) {
        Report report = context.entityWithMetadata().entity();

        // Check if entity is null (structural validation)
        if (report == null) {
            logger.warn("Report is null");
            return EvaluationOutcome.fail("Report entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Check if recipient count is null, which indicates send failure
        if (report.getRecipientCount() == null) {
            logger.warn("Email sending failed for Report: {} - recipient count is null", report.getReportId());
            return EvaluationOutcome.fail("Email sending failed - no recipient count recorded", 
                                        StandardEvalReasonCategories.EXTERNAL_DEPENDENCY_FAILURE);
        }

        // Check if recipient count is zero, which indicates no emails were sent
        if (report.getRecipientCount() == 0) {
            logger.warn("Email sending failed for Report: {} - no recipients", report.getReportId());
            return EvaluationOutcome.fail("Email sending failed - no emails were sent to recipients", 
                                        StandardEvalReasonCategories.EXTERNAL_DEPENDENCY_FAILURE);
        }

        // Check if recipient count is negative (invalid state)
        if (report.getRecipientCount() < 0) {
            logger.warn("Email sending failed for Report: {} - invalid recipient count: {}", 
                       report.getReportId(), report.getRecipientCount());
            return EvaluationOutcome.fail("Email sending failed - invalid recipient count", 
                                        StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // If we reach here, email sending was successful
        logger.debug("Email sending was successful for Report: {}, sent to {} recipients", 
                    report.getReportId(), report.getRecipientCount());
        return EvaluationOutcome.success();
    }
}
