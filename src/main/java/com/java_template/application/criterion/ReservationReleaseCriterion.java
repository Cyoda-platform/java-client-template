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

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
public class ReservationReleaseCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReservationReleaseCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Reservation.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        // Must match exact criterion name (case-sensitive)
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Reservation> context) {
         Reservation entity = context.entity();

         // Basic required checks using only available getters
         if (entity == null) {
             return EvaluationOutcome.fail("Reservation entity missing", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // qty must be present and > 0
         if (entity.getQty() == null || entity.getQty() <= 0) {
             return EvaluationOutcome.fail("Reservation.qty must be > 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // status must be present
         if (entity.getStatus() == null || entity.getStatus().isBlank()) {
             return EvaluationOutcome.fail("Reservation.status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = entity.getStatus().trim().toUpperCase(Locale.ROOT);

         // Allowed statuses per functional model: ACTIVE, EXPIRED, RELEASED, COMMITTED
         if (!(status.equals("ACTIVE") || status.equals("EXPIRED") || status.equals("RELEASED") || status.equals("COMMITTED"))) {
             return EvaluationOutcome.fail("Reservation.status has unexpected value: " + entity.getStatus(), StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         String expiresAt = entity.getExpiresAt();

         // Now apply status-specific rules around expiresAt temporal consistency
         OffsetDateTime now = OffsetDateTime.now();

         if (status.equals("ACTIVE")) {
             // Active reservations must have a future expiresAt
             if (expiresAt == null || expiresAt.isBlank()) {
                 return EvaluationOutcome.fail("Active reservation must have expiresAt set", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             try {
                 OffsetDateTime exp = OffsetDateTime.parse(expiresAt);
                 if (!exp.isAfter(now)) {
                     return EvaluationOutcome.fail("Active reservation has expiresAt in the past or now", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             } catch (DateTimeParseException e) {
                 logger.debug("Failed to parse expiresAt for reservation {}: {}", entity.getReservationId(), expiresAt);
                 return EvaluationOutcome.fail("Reservation.expiresAt is not a valid ISO-8601 datetime", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else if (status.equals("EXPIRED")) {
             // Expired reservations must have an expiresAt in the past or now
             if (expiresAt == null || expiresAt.isBlank()) {
                 return EvaluationOutcome.fail("Expired reservation must have expiresAt set", StandardEvalReasonCategories.VALIDATION_FAILURE);
             }
             try {
                 OffsetDateTime exp = OffsetDateTime.parse(expiresAt);
                 if (exp.isAfter(now)) {
                     return EvaluationOutcome.fail("Expired reservation has expiresAt in the future", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                 }
             } catch (DateTimeParseException e) {
                 logger.debug("Failed to parse expiresAt for reservation {}: {}", entity.getReservationId(), expiresAt);
                 return EvaluationOutcome.fail("Reservation.expiresAt is not a valid ISO-8601 datetime", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         } else if (status.equals("RELEASED")) {
             // Released reservations should not have a future expiresAt (they may have none or past)
             if (expiresAt != null && !expiresAt.isBlank()) {
                 try {
                     OffsetDateTime exp = OffsetDateTime.parse(expiresAt);
                     if (exp.isAfter(now)) {
                         return EvaluationOutcome.fail("Released reservation has expiresAt in the future", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                     }
                 } catch (DateTimeParseException e) {
                     logger.debug("Failed to parse expiresAt for reservation {}: {}", entity.getReservationId(), expiresAt);
                     return EvaluationOutcome.fail("Reservation.expiresAt is not a valid ISO-8601 datetime", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         } else if (status.equals("COMMITTED")) {
             // Committed reservations are expected to have been captured for an order.
             // We perform a light check: expiresAt may be present but if present it must not be in the future (commit implies hold ended)
             if (expiresAt != null && !expiresAt.isBlank()) {
                 try {
                     OffsetDateTime exp = OffsetDateTime.parse(expiresAt);
                     if (exp.isAfter(now)) {
                         return EvaluationOutcome.fail("Committed reservation has expiresAt in the future", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
                     }
                 } catch (DateTimeParseException e) {
                     logger.debug("Failed to parse expiresAt for reservation {}: {}", entity.getReservationId(), expiresAt);
                     return EvaluationOutcome.fail("Reservation.expiresAt is not a valid ISO-8601 datetime", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
                 }
             }
         }

         // All checks passed
        return EvaluationOutcome.success();
    }
}