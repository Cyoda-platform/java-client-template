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
        // This is a predefined chain. Just write the business logic in processEntityLogic method.
        return serializer.withRequest(request) //always use this method name to request EntityCriteriaCalculationResponse
            .evaluateEntity(Booking.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Booking> context) {
         Booking entity = context.entity();
         if (entity == null) {
             logger.warn("ValidationFailedCriterion invoked with null entity");
             return EvaluationOutcome.fail("Entity is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Required fields
         if (entity.getBookingId() == null) {
             return EvaluationOutcome.fail("bookingId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getFirstName() == null || entity.getFirstName().isBlank()) {
             return EvaluationOutcome.fail("firstName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getLastName() == null || entity.getLastName().isBlank()) {
             return EvaluationOutcome.fail("lastName is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getDepositPaid() == null) {
             return EvaluationOutcome.fail("depositPaid is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (entity.getTotalPrice() == null) {
             return EvaluationOutcome.fail("totalPrice is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Data quality: totalPrice should be non-negative
         if (entity.getTotalPrice() != null && entity.getTotalPrice() < 0) {
             return EvaluationOutcome.fail("totalPrice must be non-negative", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         // Booking dates checks
         Booking.BookingDates bd = entity.getBookingDates();
         if (bd == null) {
             return EvaluationOutcome.fail("bookingDates is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         String checkinStr = bd.getCheckin();
         String checkoutStr = bd.getCheckout();
         if (checkinStr == null || checkinStr.isBlank()) {
             return EvaluationOutcome.fail("bookingDates.checkin is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }
         if (checkoutStr == null || checkoutStr.isBlank()) {
             return EvaluationOutcome.fail("bookingDates.checkout is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // Parse dates and ensure logical ordering: checkin < checkout
         LocalDate checkin;
         LocalDate checkout;
         try {
             checkin = LocalDate.parse(checkinStr);
             checkout = LocalDate.parse(checkoutStr);
         } catch (DateTimeParseException e) {
             logger.debug("Date parsing error for bookingId {}: {}", entity.getBookingId(), e.getMessage());
             return EvaluationOutcome.fail("Invalid date format for bookingDates (expected ISO date)", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
         }

         if (!checkin.isBefore(checkout)) {
             return EvaluationOutcome.fail("bookingDates.checkin must be before bookingDates.checkout", StandardEvalReasonCategories.VALIDATION_FAILURE);
         }

         // All checks passed
         return EvaluationOutcome.success();
    }
}