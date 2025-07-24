package com.java_template.application.criterion;

import com.java_template.application.entity.EmailNotification;
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
public class EmailSentCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public EmailSentCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("EmailSentCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(EmailNotification.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "EmailSentCriterion".equals(modelSpec.operationName()) &&
               "emailNotification".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(EmailNotification entity) {
        if (entity.getEmailSentStatus() == null || entity.getEmailSentStatus().isBlank()) {
            return EvaluationOutcome.fail("Email sent status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if ("SENT".equalsIgnoreCase(entity.getEmailSentStatus())) {
            return EvaluationOutcome.success();
        } else if ("FAILED".equalsIgnoreCase(entity.getEmailSentStatus())) {
            return EvaluationOutcome.fail("Email sending failed", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        } else if ("PENDING".equalsIgnoreCase(entity.getEmailSentStatus())) {
            return EvaluationOutcome.fail("Email sending pending", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        } else {
            return EvaluationOutcome.fail("Unknown email sent status", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }
    }
}
