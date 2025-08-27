package com.java_template.application.criterion;

import com.java_template.application.entity.shipment.version_1.Shipment;
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

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

@Component
public class AutoDeliverAfterDelayCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public AutoDeliverAfterDelayCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Shipment.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Shipment> context) {
         Shipment entity = context.entity();

         if (entity == null) {
             return EvaluationOutcome.fail("Shipment entity is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String status = entity.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("Shipment.status is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Only consider shipments that are in SENT state for auto-delivery
         if (!"SENT".equals(status)) {
             return EvaluationOutcome.fail("Shipment is not in SENT state", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         String updatedAt = entity.getUpdatedAt();
         if (updatedAt == null || updatedAt.isBlank()) {
             return EvaluationOutcome.fail("Shipment.updatedAt is missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         Instant updatedInstant;
         try {
             updatedInstant = Instant.parse(updatedAt);
         } catch (DateTimeParseException ex) {
             logger.warn("Failed to parse shipment.updatedAt [{}] for shipmentId={}", updatedAt, entity.getShipmentId(), ex);
             return EvaluationOutcome.fail("Shipment.updatedAt is not a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Auto-deliver after configured delay (5 seconds)
         Duration delay = Duration.ofSeconds(5);
         Instant now = Instant.now();
         if (now.isAfter(updatedInstant.plus(delay)) || now.equals(updatedInstant.plus(delay))) {
             return EvaluationOutcome.success();
         } else {
             long secondsRemaining = delay.minus(Duration.between(updatedInstant, now)).getSeconds();
             if (secondsRemaining < 0) secondsRemaining = 0;
             return EvaluationOutcome.fail("Shipment has not reached auto-delivery delay yet (seconds remaining: " + secondsRemaining + ")", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }
    }
}