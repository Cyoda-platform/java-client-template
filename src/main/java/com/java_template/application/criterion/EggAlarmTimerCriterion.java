package com.java_template.application.criterion;

import com.java_template.application.entity.eggalarm.version_1.EggAlarm;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * EggAlarmTimerCriterion - Checks if cooking time has elapsed
 * 
 * This criterion evaluates whether the cooking time has elapsed for an active egg alarm
 * to determine if it should automatically transition to COMPLETED state.
 * 
 * This is a pure function that only evaluates the condition without modifying any data.
 */
@Component
public class EggAlarmTimerCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public EggAlarmTimerCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking EggAlarm timer criteria for request: {}", request.getId());
        
        return serializer.withRequest(request)
            .evaluateEntity(EggAlarm.class, this::validateTimerCondition)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Main validation logic for the timer condition
     * 
     * Checks if the cooking time has elapsed for an active egg alarm.
     * Returns true if the timer has expired and the alarm should complete.
     */
    private EvaluationOutcome validateTimerCondition(CriterionSerializer.CriterionEntityEvaluationContext<EggAlarm> context) {
        EggAlarm entity = context.entityWithMetadata().entity();
        String currentState = context.entityWithMetadata().metadata().getState();

        // Check if entity is null (structural validation)
        if (entity == null) {
            logger.warn("EggAlarm entity is null");
            return EvaluationOutcome.fail("Entity is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        // Only evaluate entities in ACTIVE state
        if (!"active".equals(currentState)) {
            logger.debug("EggAlarm is not in active state: {}", currentState);
            return EvaluationOutcome.fail("Entity is not in active state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }

        // Validate required fields for timer evaluation
        if (entity.getStartedAt() == null) {
            logger.warn("EggAlarm startedAt is null");
            return EvaluationOutcome.fail("StartedAt timestamp is null", StandardEvalReasonCategories.STRUCTURAL_FAILURE);
        }

        if (entity.getCookingTimeMinutes() == null || entity.getCookingTimeMinutes() <= 0) {
            logger.warn("EggAlarm cookingTimeMinutes is invalid: {}", entity.getCookingTimeMinutes());
            return EvaluationOutcome.fail("CookingTimeMinutes is invalid", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        // Calculate elapsed time
        LocalDateTime now = LocalDateTime.now();
        long elapsedTimeMinutes = ChronoUnit.MINUTES.between(entity.getStartedAt(), now);

        logger.debug("EggAlarm {} - Elapsed time: {} minutes, Required time: {} minutes", 
                    entity.getId(), elapsedTimeMinutes, entity.getCookingTimeMinutes());

        // Check if cooking time has elapsed
        if (elapsedTimeMinutes >= entity.getCookingTimeMinutes()) {
            logger.info("EggAlarm {} timer has expired - ready to complete", entity.getId());
            return EvaluationOutcome.success();
        } else {
            logger.debug("EggAlarm {} still cooking - {} minutes remaining", 
                        entity.getId(), entity.getCookingTimeMinutes() - elapsedTimeMinutes);
            return EvaluationOutcome.fail("Timer has not expired yet", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
        }
    }
}
