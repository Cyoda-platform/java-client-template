package com.java_template.application.processor;
import com.java_template.application.entity.flightSearch.version_1.FlightSearch;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Component
public class ValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private static final Pattern IATA = Pattern.compile("^[A-Z]{3}$");

    public ValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing FlightSearch validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FlightSearch.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FlightSearch entity) {
        return entity != null; // basic guard; detailed validation in processEntityLogic
    }

    private FlightSearch processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FlightSearch> context) {
        FlightSearch entity = context.entity();
        try {
            logger.debug("Setting status -> VALIDATING for search {}", entity.getTechnicalId());
            entity.setStatus("VALIDATING");
            entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());

            // Field presence checks
            if (entity.getOriginAirportCode() == null || entity.getOriginAirportCode().isEmpty()) {
                entity.setStatus("ERROR");
                entity.setErrorMessage("originAirportCode is required");
                entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                return entity;
            }
            if (entity.getDepartureDate() == null || entity.getDepartureDate().isEmpty()) {
                entity.setStatus("ERROR");
                entity.setErrorMessage("departureDate is required");
                entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                return entity;
            }

            // IATA validation
            if (!IATA.matcher(entity.getOriginAirportCode()).matches()) {
                entity.setStatus("ERROR");
                entity.setErrorMessage("Invalid originAirportCode format");
                entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                return entity;
            }
            if (entity.getDestinationAirportCode() != null && !entity.getDestinationAirportCode().isEmpty()
                && !IATA.matcher(entity.getDestinationAirportCode()).matches()) {
                entity.setStatus("ERROR");
                entity.setErrorMessage("Invalid destinationAirportCode format");
                entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                return entity;
            }

            // Date validation
            try {
                LocalDate departure = LocalDate.parse(entity.getDepartureDate());
                if (entity.getReturnDate() != null && !entity.getReturnDate().isEmpty()) {
                    LocalDate ret = LocalDate.parse(entity.getReturnDate());
                    if (departure.isAfter(ret)) {
                        entity.setStatus("ERROR");
                        entity.setErrorMessage("departureDate must be before or equal to returnDate");
                        entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                        return entity;
                    }
                }
            } catch (DateTimeParseException e) {
                entity.setStatus("ERROR");
                entity.setErrorMessage("Invalid date format; expected ISO-8601 (yyyy-MM-dd)");
                entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                return entity;
            }

            // passengerCount
            Integer pax = entity.getPassengerCount();
            if (pax == null || pax < 1) {
                entity.setStatus("ERROR");
                entity.setErrorMessage("passengerCount must be >= 1");
                entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                return entity;
            }
            if (pax > 9) { // business rule: max 9
                entity.setStatus("ERROR");
                entity.setErrorMessage("passengerCount exceeds allowed maximum of 9");
                entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                return entity;
            }

            // cabin class validation (if present)
            if (entity.getCabinClass() != null && !entity.getCabinClass().isEmpty()) {
                String cls = entity.getCabinClass();
                if (!("ECONOMY".equalsIgnoreCase(cls) || "PREMIUM_ECONOMY".equalsIgnoreCase(cls)
                    || "BUSINESS".equalsIgnoreCase(cls) || "FIRST".equalsIgnoreCase(cls))) {
                    entity.setStatus("ERROR");
                    entity.setErrorMessage("Invalid cabinClass value");
                    entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
                    return entity;
                }
            }

            // All validations passed: allow transition to QUERYING by clearing error and updating status
            entity.setStatus("VALIDATING");
            entity.setErrorMessage(null);
            entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error while validating FlightSearch {}", entity.getTechnicalId(), ex);
            entity.setStatus("ERROR");
            entity.setErrorMessage("Validation processor unexpected error: " + ex.getMessage());
            entity.setUpdatedAt(java.time.OffsetDateTime.now().toString());
            return entity;
        }
    }
}
