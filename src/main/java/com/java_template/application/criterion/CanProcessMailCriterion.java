package com.java_template.application.criterion;

import com.java_template.application.entity.Mail;
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
public class CanProcessMailCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public CanProcessMailCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("CanProcessMailCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(Mail.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "CanProcessMailCriterion".equals(modelSpec.operationName()) &&
                "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Mail entity) {
        if (entity.getId() == null || entity.getId().isBlank()) {
            return EvaluationOutcome.fail("ID must not be null or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getTechnicalId() == null) {
            return EvaluationOutcome.fail("Technical ID must not be null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getIsHappy() == null) {
            return EvaluationOutcome.fail("isHappy must not be null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getMailList() == null || entity.getMailList().isEmpty()) {
            return EvaluationOutcome.fail("mailList must not be null or empty", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        for (String mail : entity.getMailList()) {
            if (mail == null || mail.isBlank()) {
                return EvaluationOutcome.fail("mailList contains null or blank email", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }
        if (entity.getContent() == null || entity.getContent().isBlank()) {
            return EvaluationOutcome.fail("content must not be null or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (entity.getStatus() == null || entity.getStatus().isBlank()) {
            return EvaluationOutcome.fail("status must not be null or blank", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
