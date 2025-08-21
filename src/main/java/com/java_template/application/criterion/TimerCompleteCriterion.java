package com.java_template.application.criterion;

import com.java_template.application.entity.eggtimer.version_1.EggTimer;
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

import java.time.Instant;

@Component
public class TimerCompleteCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public TimerCompleteCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(EggTimer.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<EggTimer> context) {
        EggTimer timer = context.entity();
        if (timer == null) return EvaluationOutcome.fail("Timer not present", StandardEvalReasonCategories.VALIDATION_FAILURE);

        String state = timer.getState();
        if (state == null) return EvaluationOutcome.fail("Timer has no state", StandardEvalReasonCategories.VALIDATION_FAILURE);
        if (!"RUNNING".equalsIgnoreCase(state)) {
            return EvaluationOutcome.fail("Timer not in RUNNING state", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        String expected = timer.getExpectedEndAt();
        if (expected == null) return EvaluationOutcome.fail("No expectedEndAt set", StandardEvalReasonCategories.VALIDATION_FAILURE);

        try {
            Instant expectedInstant = Instant.parse(expected);
            Instant now = Instant.now();
            if (!now.isBefore(expectedInstant) || now.equals(expectedInstant)) {
                return EvaluationOutcome.success();
            } else {
                return EvaluationOutcome.fail("Timer not yet elapsed", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (Exception ex) {
            logger.warn("Invalid expectedEndAt format for timer {}: {}", timer.getId(), expected);
            return EvaluationOutcome.fail("Invalid expectedEndAt format", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
    }
}
