package com.java_template.application.criterion;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class BookingCapacityAvailableCriterion implements CyodaCriterion {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CriterionSerializer serializer;
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;

    public BookingCapacityAvailableCriterion(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        this.entityService = entityService;
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

        if (booking.getEventId() == null || booking.getEventId().isEmpty()) {
            return EvaluationOutcome.fail("Booking eventId is missing", StandardEvalReasonCategories.VALIDATION_FAILURE);
        }

        try {
            CompletableFuture<ObjectNode> eventFuture = entityService.getItem(
                Event.ENTITY_NAME,
                String.valueOf(Event.ENTITY_VERSION),
                java.util.UUID.fromString(booking.getEventId())
            );
            ObjectNode eventNode = eventFuture.get();

            if (eventNode == null) {
                return EvaluationOutcome.fail("Event not found", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            Integer capacity = eventNode.hasNonNull("capacity") ? eventNode.get("capacity").asInt() : null;
            if (capacity == null) {
                return EvaluationOutcome.fail("Event capacity not specified", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
            }

            // Calculate current bookings for this event
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.eventId", "EQUALS", booking.getEventId())
            );
            CompletableFuture<ArrayNode> bookingsFuture = entityService.getItemsByCondition(
                Booking.ENTITY_NAME,
                String.valueOf(Booking.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode bookingsArray = bookingsFuture.get();

            int totalBooked = 0;
            for (int i = 0; i < bookingsArray.size(); i++) {
                ObjectNode bNode = (ObjectNode) bookingsArray.get(i);
                if (bNode.hasNonNull("numberOfTickets") && bNode.hasNonNull("bookingStatus")) {
                    String bStatus = bNode.get("bookingStatus").asText();
                    if (!bStatus.equalsIgnoreCase("CANCELLED")) {
                        totalBooked += bNode.get("numberOfTickets").asInt();
                    }
                }
            }

            if (booking.getNumberOfTickets() > (capacity - totalBooked)) {
                return EvaluationOutcome.fail("Not enough capacity for booking", StandardEvalReasonCategories.VALIDATION_FAILURE);
            }

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while checking event capacity", e);
            return EvaluationOutcome.fail("Error while checking event capacity", StandardEvalReasonCategories.DATA_QUALITY_FAILURE);
        }

        return EvaluationOutcome.success();
    }
}
