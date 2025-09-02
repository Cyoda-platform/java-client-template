package com.java_template.application.criterion;

import com.java_template.application.entity.mail.version_1.Mail;
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
public class MailIsGloomyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public MailIsGloomyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking if mail is gloomy for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(Mail.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Mail> context) {
        Mail entity = context.entity();
        
        if (entity == null) {
            logger.warn("Mail entity is null");
            return EvaluationOutcome.fail("Mail entity is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (entity.getIsHappy() == null) {
            logger.warn("isHappy field is null");
            return EvaluationOutcome.fail("isHappy field is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (!(entity.getIsHappy() instanceof Boolean)) {
            logger.warn("isHappy field is not a boolean type");
            return EvaluationOutcome.fail("isHappy field is not a boolean type", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        
        if (!entity.getIsHappy()) {
            logger.debug("Mail is marked as gloomy");
            return EvaluationOutcome.success();
        } else {
            logger.debug("Mail is not marked as gloomy");
            return EvaluationOutcome.fail("Mail is not marked as gloomy", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
