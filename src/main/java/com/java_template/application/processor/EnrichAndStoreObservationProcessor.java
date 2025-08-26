package com.java_template.application.processor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.location.version_1.Location;
import com.java_template.application.entity.weatherobservation.version_1.WeatherObservation;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class EnrichAndStoreObservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichAndStoreObservationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public EnrichAndStoreObservationProcessor(SerializerFactory serializerFactory,
                                              EntityService entityService,
                                              ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeatherObservation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(WeatherObservation.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeatherObservation entity) {
        return entity != null && entity.isValid();
    }

    private WeatherObservation processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeatherObservation> context) {
        WeatherObservation entity = context.entity();
        if (entity == null) return null;

        try {
            // Basic normalization and sanity fixes (only using existing fields)
            // Clamp humidity to valid range if present
            if (entity.getHumidity() != null) {
                Integer h = entity.getHumidity();
                if (h < 0) h = 0;
                if (h > 100) h = 100;
                entity.setHumidity(h);
            }

            // Ensure temperature is a finite number and round to 1 decimal place
            if (entity.getTemperature() != null) {
                Double t = entity.getTemperature();
                if (Double.isNaN(t) || Double.isInfinite(t)) {
                    logger.warn("Invalid temperature value for observationId={}", entity.getObservationId());
                    // Keep original value; entity.isValid() would have failed earlier if invalid
                } else {
                    double rounded = Math.round(t * 10.0) / 10.0;
                    entity.setTemperature(rounded);
                }
            }

            // Ensure precipitation and windSpeed are non-negative and finite
            if (entity.getPrecipitation() != null) {
                Double p = entity.getPrecipitation();
                if (Double.isNaN(p) || Double.isInfinite(p) || p < 0) {
                    logger.warn("Normalizing precipitation for observationId={} (was {})", entity.getObservationId(), p);
                    entity.setPrecipitation(Math.max(0.0, (p == null || Double.isNaN(p) || Double.isInfinite(p)) ? 0.0 : p));
                }
            }

            if (entity.getWindSpeed() != null) {
                Double w = entity.getWindSpeed();
                if (Double.isNaN(w) || Double.isInfinite(w) || w < 0) {
                    logger.warn("Normalizing windSpeed for observationId={} (was {})", entity.getObservationId(), w);
                    entity.setWindSpeed(Math.max(0.0, (w == null || Double.isNaN(w) || Double.isInfinite(w)) ? 0.0 : w));
                }
            }

            // Try to locate matching Location entity by domain locationId.
            // If location exists and is active, mark processed = true; otherwise leave processed as false and log.
            if (entity.getLocationId() != null && !entity.getLocationId().isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group(
                    "AND",
                    Condition.of("$.locationId", "EQUALS", entity.getLocationId())
                );

                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION),
                    condition,
                    true
                );

                ArrayNode results = itemsFuture != null ? itemsFuture.join() : null;
                if (results != null && results.size() > 0) {
                    JsonNode locNode = results.get(0);
                    try {
                        Location loc = objectMapper.treeToValue(locNode, Location.class);
                        if (loc != null && Boolean.TRUE.equals(loc.getActive())) {
                            // Location is active -> mark processed
                            entity.setProcessed(true);
                            logger.info("Enriched WeatherObservation {}: matched active Location {}", entity.getObservationId(), loc.getLocationId());
                        } else {
                            // Location found but not active
                            entity.setProcessed(false);
                            logger.warn("WeatherObservation {} refers to inactive Location {}", entity.getObservationId(), entity.getLocationId());
                        }
                    } catch (Exception e) {
                        // Couldn't map location node; do not fail the processor - log and preserve processed flag as-is
                        logger.warn("Failed to map Location for enrichment for observationId={} : {}", entity.getObservationId(), e.getMessage());
                    }
                } else {
                    // No matching location found; do not mark processed and log info
                    entity.setProcessed(false);
                    logger.warn("No Location found for locationId={} while enriching observationId={}", entity.getLocationId(), entity.getObservationId());
                }
            } else {
                // No locationId present - cannot enrich; keep processed flag false to indicate not ready
                entity.setProcessed(false);
                logger.warn("Observation {} missing locationId; cannot enrich", entity.getObservationId());
            }

        } catch (Exception ex) {
            // Log unexpected errors but do not throw to avoid breaking workflow; ensure processed flag false
            logger.error("Error while enriching observationId={}: {}", entity.getObservationId(), ex.getMessage(), ex);
            entity.setProcessed(false);
        }

        // Return modified entity; Cyoda will persist this entity as part of the workflow.
        return entity;
    }
}