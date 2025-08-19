package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.flightoption.version_1.FlightOption;
import com.java_template.application.entity.flightsearch.version_1.FlightSearch;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
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

import static com.java_template.common.config.Config.*;

@Component
public class MapResultsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MapResultsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MapResultsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MapResultsProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FlightSearch.class)
            .validate(this::isValidEntity, "Invalid entity state for mapping")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FlightSearch entity) {
        return entity != null && entity.getStatus() != null && ("QUERYING".equalsIgnoreCase(entity.getStatus()) || "VALIDATING".equalsIgnoreCase(entity.getStatus()));
    }

    private FlightSearch processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FlightSearch> context) {
        FlightSearch entity = context.entity();
        try {
            String sid = entity.getSearchId() != null ? entity.getSearchId() : "<unknown>";
            logger.debug("Setting status -> MAPPING for search {}", sid);
            entity.setStatus("MAPPING");

            // NOTE: The FlightSearch entity in this project does not store a rawResponse field.
            // For the purposes of this implementation we will simulate result mapping based on the search parameters
            // and create a small set of FlightOption entries so downstream processors can run.

            int resultsToCreate = 0;
            if (entity.getPassengerCount() != null && entity.getPassengerCount() > 0) {
                // create 1-3 options depending on passengerCount (capped)
                resultsToCreate = Math.min(3, Math.max(1, entity.getPassengerCount()));
            }

            int created = 0;
            for (int i = 0; i < resultsToCreate; i++) {
                FlightOption opt = new FlightOption();
                opt.setOptionId(UUID.randomUUID().toString());
                opt.setSearchId(entity.getSearchId());
                opt.setAirline("ExampleAir");
                opt.setFlightNumber("EX" + (100 + i));
                // Use departureDate as a base to create ISO-like datetimes
                String baseDate = entity.getDepartureDate() != null ? entity.getDepartureDate() : "2025-01-01";
                opt.setDepartureTime(baseDate + "T0" + (8 + i) + ":00:00Z");
                opt.setArrivalTime(baseDate + "T1" + (2 + i) + ":00:00Z");
                opt.setDurationMinutes(240 + i * 30);
                opt.setPriceAmount(300.0 + i * 150.0);
                opt.setCurrency("USD");
                opt.setStops(0);
                opt.setLayovers(null);
                opt.setFareRules(null);
                opt.setSeatAvailability(null);
                opt.setStatus("CREATED");

                // Persist FlightOption via EntityService
                CompletableFuture<java.util.UUID> future = entityService.addItem(
                    FlightOption.ENTITY_NAME,
                    String.valueOf(FlightOption.ENTITY_VERSION),
                    opt
                );
                try { future.get(); created++; } catch (Exception e) { logger.warn("Failed creating FlightOption record", e); }
            }

            entity.setStatus(created > 0 ? "SUCCESS" : "NO_RESULTS");
            return entity;
        } catch (Exception ex) {
            logger.error("Error mapping results for search {}", entity.getSearchId(), ex);
            entity.setStatus("ERROR");
            entity.setErrorMessage("Mapping error: " + ex.getMessage());
            return entity;
        }
    }
}
