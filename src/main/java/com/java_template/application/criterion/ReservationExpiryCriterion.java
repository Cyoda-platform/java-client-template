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
public class ReservationExpiryCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ReservationExpiryCriterion(SerializerFactory serializerFactory) {
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

         // Basic required fields validation using available getters
         if (reservation.getExpiresAt() == null || reservation.getExpiresAt().isBlank()) {
             return EvaluationOutcome.fail("expiresAt is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (reservation.getStatus() == null || reservation.getStatus().isBlank()) {
             return EvaluationOutcome.fail("status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (reservation.getQty() == null || reservation.getQty() <= 0) {
             return EvaluationOutcome.fail("qty must be greater than 0", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Parse expiresAt and compare with current time
         Instant now = Instant.now();
         Instant expiresAtInstant;
         try {
             expiresAtInstant = Instant.parse(reservation.getExpiresAt());
         } catch (DateTimeParseException e) {
             logger.warn("Invalid expiresAt format for reservation id {}: {}", reservation.getId(), reservation.getExpiresAt());
             return EvaluationOutcome.fail("expiresAt must be an ISO-8601 timestamp", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         boolean isExpired = !expiresAtInstant.isAfter(now); // expired if expiresAt <= now
         String status = reservation.getStatus().trim();

         // If reservation has expired according to expiresAt, its status should be EXPIRED (business rule)
         if (isExpired) {
             if (!"EXPIRED".equalsIgnoreCase(status)) {
                 return EvaluationOutcome.fail("Reservation has passed its expiresAt but status is not EXPIRED", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
             // status is EXPIRED and expiresAt is past -> consistent
             return EvaluationOutcome.success();
         } else {
             // Not yet expired: status should not be EXPIRED
             if ("EXPIRED".equalsIgnoreCase(status)) {
                 return EvaluationOutcome.fail("Reservation marked EXPIRED but expiresAt is in the future", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
             }
             // Active or Released statuses are acceptable here; nothing to enforce beyond consistency
             return EvaluationOutcome.success();
         }
    }
}