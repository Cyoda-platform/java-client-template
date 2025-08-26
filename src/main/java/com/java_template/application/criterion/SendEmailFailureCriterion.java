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
public class SendEmailFailureCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public SendEmailFailureCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(AnalysisReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name
        return "SendEmailFailureCriterion".equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<AnalysisReport> context) {
        AnalysisReport entity = context.entity();

        if (entity == null) {
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // reportId is required to identify the report being transitioned
        if (entity.getReportId() == null || entity.getReportId().isBlank()) {
            return EvaluationOutcome.fail("Missing report id", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // recipientEmail must be present for any send-related transitions
        if (entity.getRecipientEmail() == null || entity.getRecipientEmail().isBlank()) {
            return EvaluationOutcome.fail("Missing recipient email", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // status must be present to evaluate a failure transition
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Missing report status", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Only allow the transition to FAILED when the report is actually in FAILED state
        if ("FAILED".equalsIgnoreCase(entity.getStatus())) {
            logger.info("SendEmailFailureCriterion: AnalysisReport {} is in FAILED state, allowing failure transition", entity.getReportId());
            return EvaluationOutcome.success();
        }

        // Otherwise, disallow the failure transition
        return EvaluationOutcome.fail("Report not in FAILED state (current: " + entity.getStatus() + ")", StandardEvalReasonCategories.VALIDATION_FAILURE);
    }
}