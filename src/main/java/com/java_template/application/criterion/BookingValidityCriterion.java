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
public class BookingValidityCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BookingValidityCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        return serializer.withRequest(request)
            .evaluateEntity(Booking.class, this::validateEntity)
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private EvaluationOutcome validateEntity(CriterionSerializer.CriterionEntityEvaluationContext<Booking> context) {
        Booking b = context.entity();
        if (b == null) {
            return EvaluationOutcome.fail("Booking is null", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (b.getBookingId() == null || b.getBookingId().isEmpty()) {
            return EvaluationOutcome.fail("bookingId is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Dates presence and sanity
        if (b.getCheckInDate() == null || b.getCheckInDate().isEmpty()) {
            return EvaluationOutcome.fail("checkInDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (b.getCheckOutDate() == null || b.getCheckOutDate().isEmpty()) {
            return EvaluationOutcome.fail("checkOutDate is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        try {
            LocalDate in = LocalDate.parse(b.getCheckInDate());
            LocalDate out = LocalDate.parse(b.getCheckOutDate());
            if (!in.isBefore(out) && !in.equals(out)) {
                return EvaluationOutcome.fail("checkOutDate must be after checkInDate", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        } catch (DateTimeParseException e) {
            return EvaluationOutcome.fail("Invalid date format for checkInDate/checkOutDate", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        // Price non-negative
        if (b.getTotalPrice() != null) {
            if (b.getTotalPrice() < 0) {
                return EvaluationOutcome.fail("totalPrice cannot be negative", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }
        }

        return EvaluationOutcome.success();
    }
}
