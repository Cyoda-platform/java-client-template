package com.java_template.application.criterion;

import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.application.entity.event.version_1.Event;
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
public class BookingCapacityAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public BookingCapacityAvailableCriterion(SerializerFactory serializerFactory) {
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

        // Business logic:
        // Check that event capacity is sufficient for the number of tickets in the booking
        // Assume method to fetch event by eventId is available via context or service

        // For demo, we simulate event capacity check (should be replaced by actual service call)
        Integer capacity = getEventCapacity(booking.getEventId());
        if (capacity == null) {
            return EvaluationOutcome.fail("Event not found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        if (booking.getNumberOfTickets() > capacity) {
            return EvaluationOutcome.fail("Not enough capacity for booking", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        return EvaluationOutcome.success();
    }

    private Integer getEventCapacity(String eventId) {
        // TODO: Implement actual event retrieval logic
        // Simulated static capacity for demonstration
        if (eventId == null || eventId.isEmpty()) {
            return null;
        }
        if (eventId.equals("event123")) {
            return 100;
        }
        return 50; // default capacity
    }
}
