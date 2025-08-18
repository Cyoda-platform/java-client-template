package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
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
public class DeliveryFailuresExceededCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public DeliveryFailuresExceededCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Subscriber> context) {
        Subscriber s = context.entity();
        if (s == null) return EvaluationOutcome.fail("Subscriber null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        Integer failureCount = s.getFailureCount();
        if (failureCount == null) failureCount = 0;
        // Pause threshold default 5
        int threshold = 5;
        if (s.getMeta() != null && s.getMeta().containsKey("pauseThreshold")) {
            try {
                Object v = s.getMeta().get("pauseThreshold");
                threshold = Integer.parseInt(v.toString());
            } catch (Exception e) {
                logger.warn("Invalid pauseThreshold in subscriber meta for {}", s.getTechnicalId());
            }
        }
        if (failureCount >= threshold) {
            return EvaluationOutcome.success();
        }
        return EvaluationOutcome.fail("Failure count below threshold", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
    }
}
