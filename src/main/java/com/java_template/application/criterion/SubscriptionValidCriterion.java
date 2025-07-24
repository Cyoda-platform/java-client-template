package com.java_template.application.criterion;

import com.java_template.application.entity.Subscription;
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
public class SubscriptionValidCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;

    public SubscriptionValidCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.info("SubscriptionValidCriterion initialized with SerializerFactory");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();

        return serializer.withRequest(request)
                .evaluateEntity(Subscription.class, this::validateEntity)
                .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "SubscriptionValidCriterion".equals(modelSpec.operationName()) &&
                "subscription".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private EvaluationOutcome validateEntity(Subscription entity) {
        // Validate required fields
        if (entity.getUserId() == null || entity.getUserId().isBlank()) {
            return EvaluationOutcome.fail("User ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // team can be nullable for general NBA notifications, no validation needed

        if (entity.getNotificationType() == null) {
            return EvaluationOutcome.fail("Notification type must be specified", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getChannel() == null) {
            return EvaluationOutcome.fail("Notification channel must be specified", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getStatus() == null) {
            return EvaluationOutcome.fail("Subscription status must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        if (entity.getCreatedAt() == null) {
            return EvaluationOutcome.fail("Creation timestamp must be set", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
