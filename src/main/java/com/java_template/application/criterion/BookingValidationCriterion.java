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

@Component
public class BookingValidationCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BookingValidationCriterion(SerializerFactory serializerFactory) {
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
        Booking booking = context.entity();
        if (booking.getUserId() == null || booking.getUserId().isEmpty()) {
            return EvaluationOutcome.fail("User ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (booking.getEventId() == null || booking.getEventId().isEmpty()) {
            return EvaluationOutcome.fail("Event ID is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (booking.getTickets() == null || booking.getTickets() <= 0) {
            return EvaluationOutcome.fail("Number of tickets must be greater than zero", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (booking.getBookingDate() == null || booking.getBookingDate().isEmpty()) {
            return EvaluationOutcome.fail("Booking date is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        if (booking.getStatus() == null || booking.getStatus().isEmpty()) {
            return EvaluationOutcome.fail("Booking status is required", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }
        // Additional business rules can be added here
        return EvaluationOutcome.success();
    }
}
