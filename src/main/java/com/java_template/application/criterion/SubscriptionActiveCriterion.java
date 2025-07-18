package com.java_template.application.criterion;

import com.java_template.application.entity.Subscriber;
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
public class SubscriptionActiveCriterion implements CyodaCriterion {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public SubscriptionActiveCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("SubscriptionActiveCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, this::applySubscriptionActiveValidation)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    private EvaluationOutcome applySubscriptionActiveValidation(Subscriber subscriber) {
        if (subscriber.getStatus() != null && subscriber.getStatus().equalsIgnoreCase("ACTIVE")) {
            return EvaluationOutcome.success();
        } else {
            return EvaluationOutcome.fail("Subscriber status is not ACTIVE", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE.getCode());
        }
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SubscriptionActiveCriterion".equals(modelSpec.operationName()) &&
               "subscriber".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
