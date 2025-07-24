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
public class IsHappyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsHappyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsHappyCriterion initialized with SerializerFactory");
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
        return "IsHappyCriterion".equals(modelSpec.operationName()) &&
                "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Mail mail) {
        String content = mail.getContent();
        if (content == null || content.isBlank()) {
            return EvaluationOutcome.fail("Content is blank, cannot determine happiness", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Simple sentiment analysis based on keywords
        String lowerContent = content.toLowerCase();
        boolean isHappy = false;

        if (lowerContent.contains("happy") || lowerContent.contains("wonderful") || lowerContent.contains("joy") || lowerContent.contains("excellent") || lowerContent.contains("great")) {
            isHappy = true;
        } else if (lowerContent.contains("sad") || lowerContent.contains("gloomy") || lowerContent.contains("bad") || lowerContent.contains("terrible") || lowerContent.contains("awful")) {
            isHappy = false;
        } else {
            // If no clear sentiment, fail the evaluation
            return EvaluationOutcome.fail("Content sentiment unclear", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Update the isHappy flag accordingly
        if (mail.getIsHappy() == null || mail.getIsHappy() != isHappy) {
            // Here we might want to log or handle the update, but for criterion, just evaluate
            // No direct entity modification in validation
        }

        return EvaluationOutcome.success();
    }
}
