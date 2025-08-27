package com.java_template.application.criterion;

import com.java_template.application.entity.reservation.version_1.Reservation;
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
import java.time.format.DateTimeParseException;

@Component
public class ReservationCommitCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReservationCommitCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Reservation.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must use exact criterion name match
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Reservation> context) {
         Reservation entity = context.entity();

         if (entity == null) {
             logger.debug("Reservation entity is null in validation context");
             return EvaluationOutcome.fail("Reservation entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getReservationId() == null || entity.getReservationId().isBlank()) {
             return EvaluationOutcome.fail("reservationId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getCartId() == null || entity.getCartId().isBlank()) {
             return EvaluationOutcome.fail("cartId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getReservationBatchId() == null || entity.getReservationBatchId().isBlank()) {
             return EvaluationOutcome.fail("reservationBatchId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getSku() == null || entity.getSku().isBlank()) {
             return EvaluationOutcome.fail("sku is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getQty() == null || entity.getQty() <= 0) {
             return EvaluationOutcome.fail("qty must be a positive integer", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // The commit criterion expects the reservation to be in COMMITTED state.
         if (!"COMMITTED".equalsIgnoreCase(entity.getStatus())) {
             return EvaluationOutcome.fail("reservation must be COMMITTED to pass this criterion", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

         // Validate expiresAt is a valid ISO instant and that it was not already expired at commit time
         String expiresAt = entity.getExpiresAt();
         if (expiresAt == null || expiresAt.isBlank()) {
             return EvaluationOutcome.fail("expiresAt is required", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }
         Instant expiryInstant;
         try {
             expiryInstant = Instant.parse(expiresAt);
         } catch (DateTimeParseException e) {
             logger.debug("Invalid expiresAt format for reservation {}: {}", entity.getReservationId(), expiresAt);
             return EvaluationOutcome.fail("expiresAt must be a valid ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (Instant.now().isAfter(expiryInstant)) {
             return EvaluationOutcome.fail("cannot commit an expired reservation", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
         }

        return EvaluationOutcome.success();
    }
}