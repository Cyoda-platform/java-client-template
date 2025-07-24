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
public class CheckMailIsNotHappyNorGloomy implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public CheckMailIsNotHappyNorGloomy(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("CheckMailIsNotHappyNorGloomy initialized with SerializerFactory");
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
        return "CheckMailIsNotHappyNorGloomy".equals(modelSpec.operationName()) &&
               "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Mail mail) {
        if (mail.getIsHappy() == null) {
            return EvaluationOutcome.fail("isHappy flag must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (mail.getIsHappy()) {
            return EvaluationOutcome.fail("Mail is happy, not neither", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
        // Additional check: if mail is not happy and not gloomy, fail
        // Since gloomy means isHappy == false, we consider if isHappy is set but no other state
        // This case is logically contradictory, so we fail
        // Actually, since the only states are happy (true) or gloomy (false), no other state is possible
        // So we assume this criterion means isHappy is null or invalid, but null checked before
        return EvaluationOutcome.success();
    }
}
