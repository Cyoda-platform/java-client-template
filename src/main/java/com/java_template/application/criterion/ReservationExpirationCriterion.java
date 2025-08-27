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
public class ReservationExpirationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReservationExpirationCriterion(SerializerFactory serializerFactory) {
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
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Reservation> context) {
         Reservation reservation = context.entity();

         if (reservation == null) {
             logger.warn("Reservation entity is null in context");
             return EvaluationOutcome.fail("Reservation entity is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String expiresAt = reservation.getExpiresAt();
         if (expiresAt == null || expiresAt.isBlank()) {
             return EvaluationOutcome.fail("expiresAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Instant expires;
         try {
             expires = Instant.parse(expiresAt);
         } catch (DateTimeParseException e) {
             logger.warn("Invalid expiresAt for reservation {}: {}", reservation.getReservationId(), expiresAt, e);
             return EvaluationOutcome.fail("expiresAt must be a valid ISO-8601 timestamp", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         String status = reservation.getStatus();
         if (status == null || status.isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         Instant now = Instant.now();

         // Business rule: active reservations must have expiresAt in the future
         if ("ACTIVE".equalsIgnoreCase(status)) {
             if (!expires.isAfter(now)) {
                 return EvaluationOutcome.fail("Reservation has expired but remains ACTIVE", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         }

         // Data quality: reservation marked EXPIRED but expiresAt is still in the future
         if ("EXPIRED".equalsIgnoreCase(status)) {
             if (expires.isAfter(now)) {
                 return EvaluationOutcome.fail("Reservation is marked EXPIRED but expiresAt is in the future", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
         }

         return EvaluationOutcome.success();
    }
}