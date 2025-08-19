package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.flightOption.version_1.FlightOption;
import com.java_template.application.entity.flightSearch.version_1.FlightSearch;
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

import java.time.OffsetDateTime;
import java.util.Iterator;
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
            logger.debug("Setting status -> MAPPING for search {}", entity.getTechnicalId());
            entity.setStatus("MAPPING");
            entity.setUpdatedAt(OffsetDateTime.now().toString());

            String raw = entity.getRawResponse();
            if (raw == null || raw.isEmpty()) {
                entity.setStatus("NO_RESULTS");
                entity.setUpdatedAt(OffsetDateTime.now().toString());
                return entity;
            }

            JsonNode root = objectMapper.readTree(raw);
            ArrayNode flights = null;
            if (root.has("flights") && root.get("flights").isArray()) {
                flights = (ArrayNode) root.get("flights");
            }

            if (flights == null || flights.size() == 0) {
                entity.setStatus("NO_RESULTS");
                entity.setUpdatedAt(OffsetDateTime.now().toString());
                return entity;
            }

            int created = 0;
            Iterator<JsonNode> it = flights.elements();
            while (it.hasNext()) {
                JsonNode f = it.next();
                FlightOption opt = mapNodeToOption(f, entity.getTechnicalId());
                opt.setStatus("CREATED");
                opt.setCreatedAt(OffsetDateTime.now().toString());
                opt.setUpdatedAt(OffsetDateTime.now().toString());

                // Persist FlightOption via EntityService
                CompletableFuture<UUID> future = entityService.addItem(
                    FlightOption.ENTITY_NAME,
                    String.valueOf(FlightOption.ENTITY_VERSION),
                    opt
                );
                try { future.get(); created++; } catch (Exception e) { logger.warn("Failed creating FlightOption record", e); }
            }

            entity.setStatus(created > 0 ? "SUCCESS" : "NO_RESULTS");
            entity.setUpdatedAt(OffsetDateTime.now().toString());
            return entity;
        } catch (Exception ex) {
            logger.error("Error mapping results for search {}", entity.getTechnicalId(), ex);
            entity.setStatus("ERROR");
            entity.setErrorMessage("Mapping error: " + ex.getMessage());
            entity.setUpdatedAt(OffsetDateTime.now().toString());
            return entity;
        }
    }

    private FlightOption mapNodeToOption(JsonNode f, String searchTechnicalId) {
        FlightOption opt = new FlightOption();
        opt.setSearchTechnicalId(searchTechnicalId);
        opt.setAirline(getTextOrNull(f, "airline"));
        opt.setFlightNumber(getTextOrNull(f, "flightNumber"));
        opt.setDepartureTime(getTextOrNull(f, "departureTime"));
        opt.setArrivalTime(getTextOrNull(f, "arrivalTime"));
        opt.setDurationMinutes(getIntOrNull(f, "durationMinutes"));
        opt.setPriceAmount(getDoubleOrNull(f, "priceAmount"));
        opt.setCurrency(getTextOrNull(f, "currency"));
        opt.setStops(getIntOrNull(f, "stops"));
        opt.setLayovers(getTextOrNull(f, "layovers"));
        opt.setFareRules(getTextOrNull(f, "fareRules"));
        opt.setSeatAvailability(getIntOrNull(f, "seatAvailability"));
        return opt;
    }

    private String getTextOrNull(JsonNode n, String field) { return n.has(field) && !n.get(field).isNull() ? n.get(field).asText() : null; }
    private Integer getIntOrNull(JsonNode n, String field) { return n.has(field) && !n.get(field).isNull() ? n.get(field).asInt() : null; }
    private Double getDoubleOrNull(JsonNode n, String field) { return n.has(field) && !n.get(field).isNull() ? n.get(field).asDouble() : null; }
}
