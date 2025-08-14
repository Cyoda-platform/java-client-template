package com.java_template.application.processor;

import com.java_template.application.entity.ticket.version_1.Ticket;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class TicketValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TicketValidationProcessor.class);
    private f inal String  className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TicketValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Ticket for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Ticket.class)
            .validate(this::isValidEntity, "Invalid ticket state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Ticket ticket) {
        return ticket != null && ticket.getTicketStatus() != null && !ticket.getTicketStatus().isEmpty();
    }

    private Ticket processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Ticket> context) {
        Ticket ticket = context.entity();

        // Business logic:
        // Validate required fields
        if (ticket.getBookingId() == null || ticket.getBookingId().isEmpty()) {
            logger.error("Ticket must reference a valid bookingId");
            throw new IllegalArgumentException("Ticket must reference a valid bookingId");
        }

        if (ticket.getTicketNumber() == null || ticket.getTicketNumber().isEmpty()) {
            logger.error("Ticket number must be provided");
            throw new IllegalArgumentException("Ticket number must be provided");
        }

        if (ticket.getTicketStatus() == null || ticket.getTicketStatus().isEmpty()) {
            logger.error("Ticket status must be specified");
            throw new IllegalArgumentException("Ticket status must be specified");
        }

        // Validate ticketStatus is one of expected statuses
        String status = ticket.getTicketStatus();
        if (!status.equalsIgnoreCase("ISSUED") && !status.equalsIgnoreCase("ASSIGNED") && !status.equalsIgnoreCase("VALIDATED") && !status.equalsIgnoreCase("TRANSFERRED") && !status.equalsIgnoreCase("CANCELLED")) {
            logger.error("Invalid ticket status: {}", status);
            throw new IllegalArgumentException("Invalid ticket status: " + status);
        }

        return ticket;
    }
}
