package com.java_template.application.criterion;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
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
import java.util.Map;

/**
 * Criterion to determine if a subscriber should be suspended due to delivery issues.
 * Handles the suspend transition (active → suspended).
 * 
 * Validation Logic:
 * - Checks if bounceCount >= 3 (suspend due to hard bounces)
 * - Checks if complaintCount >= 1 (suspend due to spam complaints)
 * - Checks if lastDeliveryDate < (currentDate - 30 days) and deliveryAttempts >= 5
 *   (suspend due to persistent delivery failures)
 */
@Component
public class SubscriberSuspensionCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberSuspensionCriterion.class);
    private static final int MAX_BOUNCE_COUNT = 3;
    private static final int MAX_COMPLAINT_COUNT = 1;
    private static final int MAX_DELIVERY_ATTEMPTS = 5;
    private static final long DELIVERY_FAILURE_DAYS = 30;
    
    private final CriterionSerializer serializer;

    public SubscriberSuspensionCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("SubscriberSuspensionCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking subscriber suspension criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluateEntity(Subscriber.class, ctx -> this.evaluateSubscriberSuspension(ctx.entity()))
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "SubscriberSuspensionCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates whether a subscriber should be suspended.
     * 
     * @param subscriber The subscriber to evaluate
     * @return EvaluationOutcome indicating whether suspension criteria are met
     */
    private EvaluationOutcome evaluateSubscriberSuspension(Subscriber subscriber) {
        if (subscriber == null) {
            return EvaluationOutcome.fail("Subscriber is null");
        }

        // Check if subscriber is active (only active subscribers can be suspended)
        if (subscriber.getIsActive() == null || !subscriber.getIsActive()) {
            return EvaluationOutcome.fail("Subscriber is not active");
        }

        Map<String, Object> preferences = subscriber.getPreferences();
        if (preferences == null) {
            // No delivery issues recorded, no suspension needed
            return EvaluationOutcome.success();
        }

        // Check bounce count
        Integer bounceCount = (Integer) preferences.get("bounceCount");
        if (bounceCount != null && bounceCount >= MAX_BOUNCE_COUNT) {
            logger.info("Subscriber {} meets suspension criteria: {} hard bounces", 
                       subscriber.getEmail(), bounceCount);
            return EvaluationOutcome.success(); // Criteria met for suspension
        }

        // Check complaint count
        Integer complaintCount = (Integer) preferences.get("complaintCount");
        if (complaintCount != null && complaintCount >= MAX_COMPLAINT_COUNT) {
            logger.info("Subscriber {} meets suspension criteria: {} spam complaints", 
                       subscriber.getEmail(), complaintCount);
            return EvaluationOutcome.success(); // Criteria met for suspension
        }

        // Check persistent delivery failures
        String lastDeliveryDateStr = (String) preferences.get("lastDeliveryDate");
        Integer deliveryAttempts = (Integer) preferences.get("deliveryAttempts");
        
        if (lastDeliveryDateStr != null && deliveryAttempts != null) {
            try {
                LocalDateTime lastDeliveryDate = LocalDateTime.parse(lastDeliveryDateStr);
                LocalDateTime cutoffDate = LocalDateTime.now().minus(DELIVERY_FAILURE_DAYS, ChronoUnit.DAYS);
                
                if (lastDeliveryDate.isBefore(cutoffDate) && deliveryAttempts >= MAX_DELIVERY_ATTEMPTS) {
                    logger.info("Subscriber {} meets suspension criteria: persistent delivery failures " +
                               "(last delivery: {}, attempts: {})", 
                               subscriber.getEmail(), lastDeliveryDate, deliveryAttempts);
                    return EvaluationOutcome.success(); // Criteria met for suspension
                }
            } catch (Exception e) {
                logger.warn("Failed to parse lastDeliveryDate for subscriber {}: {}", 
                           subscriber.getEmail(), e.getMessage());
            }
        }

        // No suspension criteria met
        logger.debug("Subscriber {} does not meet suspension criteria", subscriber.getEmail());
        return EvaluationOutcome.fail("No suspension criteria met");
    }
}
