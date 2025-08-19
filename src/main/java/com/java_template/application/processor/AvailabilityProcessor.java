package com.java_template.application.processor;

import com.java_template.application.entity.flightOption.version_1.FlightOption;
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

@Component
public class AvailabilityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AvailabilityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AvailabilityProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AvailabilityProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FlightOption.class)
            .validate(this::isValidEntity, "Invalid entity state for availability check")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FlightOption entity) {
        return entity != null && entity.getStatus() != null && ("CREATED".equalsIgnoreCase(entity.getStatus()) || "ENRICHING".equalsIgnoreCase(entity.getStatus()));
    }

    private FlightOption processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FlightOption> context) {
        FlightOption entity = context.entity();
        try {
            logger.debug("Setting status -> AVAILABILITY_CHECK for option {}", entity.getTechnicalId());
            entity.setStatus("AVAILABILITY_CHECK");
            entity.setUpdatedAt(OffsetDateTime.now().toString());

            // For prototype: simulate seat check. If seatAvailability present and >0, READY. Otherwise simulate 2 seats.
            Integer seats = entity.getSeatAvailability();
            if (seats == null) seats = 2;
            entity.setSeatAvailability(seats);
            if (seats > 0) entity.setStatus("READY"); else entity.setStatus("UNAVAILABLE");
            entity.setUpdatedAt(OffsetDateTime.now().toString());
            return entity;
        } catch (Exception ex) {
            logger.error("Error checking availability for option {}", entity.getTechnicalId(), ex);
            entity.setStatus("ERROR");
            entity.setErrorMessage("Availability check failed: " + ex.getMessage());
            entity.setUpdatedAt(OffsetDateTime.now().toString());
            return entity;
        }
    }
}
