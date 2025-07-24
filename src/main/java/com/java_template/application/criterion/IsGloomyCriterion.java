package com.java_template.application.criterion;

import com.java_template.application.entity.Mail;
import com.java_template.application.entity.MailStatusEnum;
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
public class IsGloomyCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public IsGloomyCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("IsGloomyCriterion initialized with SerializerFactory");
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
        return "IsGloomyCriterion".equals(modelSpec.operationName()) &&
               "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Mail mail) {
        // Validation: mail must be marked as gloomy
        if (mail.getIsGloomy() == null || !mail.getIsGloomy()) {
            return EvaluationOutcome.fail("Mail is not marked as gloomy", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        return EvaluationOutcome.success();
    }
}
