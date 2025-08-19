package com.java_template.application.processor;

import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class PersistBookingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PersistBookingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PersistBookingProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Persisting Booking for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Booking.class)
            .validate(this::isValidEntity, "Invalid booking for persistence")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Booking booking) {
        return booking != null && booking.getBookingId() != null && !booking.getBookingId().isEmpty();
    }

    private Booking processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Booking> context) {
        Booking booking = context.entity();
        // Assign technicalId if not present
        if (booking.getTechnicalId() == null || booking.getTechnicalId().isEmpty()) {
            booking.setTechnicalId("bkg_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8));
        }
        // Set persistedAt timestamp
        booking.setPersistedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        // Ensure status is set
        if (booking.getStatus() == null || booking.getStatus().isEmpty()) {
            booking.setStatus("CONFIRMED");
        }
        logger.info("Booking persisted with technicalId={}", booking.getTechnicalId());
        return booking;
    }
}
