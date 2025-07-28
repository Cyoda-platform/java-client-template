package com.java_template.application.criterion;

import com.java_template.application.entity.EmailReport;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.StandardEvalReasonCategories;
import com.java_template.common.config.Config;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailReportCreatedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EmailReportCreatedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(EmailReport.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EmailReport> context) {

        EmailReport entity = context.entity();

        // Validate required fields for email report creation
        if (entity.getReportJobId() == null || entity.getReportJobId().isBlank()) {
            return EvaluationOutcome.fail("ReportJob ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getRecipient() == null || entity.getRecipient().isBlank()) {
            return EvaluationOutcome.fail("Recipient is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getSubject() == null || entity.getSubject().isBlank()) {
            return EvaluationOutcome.fail("Subject is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getBody() == null || entity.getBody().isBlank()) {
            return EvaluationOutcome.fail("Body is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("Status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
