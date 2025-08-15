package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class TicketReservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TicketReservationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public TicketReservationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Ticket Reservation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Booking.class)
            .validate(this::isValidEntity, "Invalid booking state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Booking entity) {
        return entity != null && entity.isValid();
    }

    private Booking processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Booking> context) {
        Booking booking = context.entity();
        logger.info("Reserving tickets for booking: {}", booking.getTechnicalId());

        try {
            // Validate event existence
            CompletableFuture<ObjectNode> eventFuture = entityService.getItem(
                    "Event",
                    "1",
                    UUID.fromString(booking.getEventId())
            );
            ObjectNode eventNode = eventFuture.join();
            if (eventNode == null || eventNode.isEmpty()) {
                logger.error("Event not found for booking: {}", booking.getTechnicalId());
                // Mark booking status as FAILED
                booking.setStatus("FAILED");
                return booking;
            }

            // Check availability of tickets
            int availableTickets = 100; // Placeholder: You can implement actual ticket availability logic here
            if (booking.getTickets() > availableTickets) {
                logger.error("Not enough tickets available for booking: {}", booking.getTechnicalId());
                booking.setStatus("FAILED");
                return booking;
            }

            // Reserve tickets logic: Update booking status
            booking.setStatus("RESERVED");
            logger.info("Tickets reserved for booking: {}", booking.getTechnicalId());

        } catch (Exception e) {
            logger.error("Error during ticket reservation for booking {}: {}", booking.getTechnicalId(), e.getMessage(), e);
            booking.setStatus("FAILED");
        }

        return booking;
    }
}
