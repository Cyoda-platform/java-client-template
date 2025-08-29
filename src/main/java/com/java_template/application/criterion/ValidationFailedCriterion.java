package com.java_template.application.criterion;

import com.java_template.application.entity.booking.version_1.Booking;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ValidationFailedCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ValidationFailedCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        // This is a predefined chain. Just write the business logic in validateEntity method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Booking.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        if (modelSpec == null || modelSpec.operationName() == null) return false;
        // Use exact criterion name (case-sensitive) as required
        return className.equals(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Booking> context) {
         Booking entity = context.entity();
         if (entity == null) {
             return EvaluationOutcome.fail("Booking entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required string fields
         if (entity.getFirstname() == null || entity.getFirstname().isBlank()) {
             return EvaluationOutcome.fail("firstname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getLastname() == null || entity.getLastname().isBlank()) {
             return EvaluationOutcome.fail("lastname is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCheckin() == null || entity.getCheckin().isBlank()) {
             return EvaluationOutcome.fail("checkin date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getCheckout() == null || entity.getCheckout().isBlank()) {
             return EvaluationOutcome.fail("checkout date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getSource() == null || entity.getSource().isBlank()) {
             return EvaluationOutcome.fail("source is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Numeric/boolean fields
         if (entity.getBookingId() == null) {
             return EvaluationOutcome.fail("bookingId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getDepositpaid() == null) {
             return EvaluationOutcome.fail("depositpaid is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getTotalprice() == null) {
             return EvaluationOutcome.fail("totalprice is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getTotalprice() < 0) {
             return EvaluationOutcome.fail("totalprice must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Business rule: checkin must be <= checkout and dates must be valid ISO (yyyy-MM-dd)
         try {
             LocalDate checkinDate = LocalDate.parse(entity.getCheckin());
             LocalDate checkoutDate = LocalDate.parse(entity.getCheckout());
             if (checkinDate.isAfter(checkoutDate)) {
                 return EvaluationOutcome.fail("checkin date must be on or before checkout date", StandardEvalReasonCategories.BUSINESS_RULE_FAILURE);
             }
         } catch (DateTimeParseException ex) {
             logger.debug("Date parsing failed for bookingId {}: {}", entity.getBookingId(), ex.getMessage());
             return EvaluationOutcome.fail("checkin/checkout must be valid ISO dates (yyyy-MM-dd)", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         return EvaluationOutcome.success();
    }
}