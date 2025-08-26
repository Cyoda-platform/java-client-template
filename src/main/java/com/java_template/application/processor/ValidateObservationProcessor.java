package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.location.version_1.Location;
import com.java_template.application.entity.weatherobservation.version_1.WeatherObservation;
import com.java_template.common.service.EntityService;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class ValidateObservationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateObservationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidateObservationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeatherObservation for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(WeatherObservation.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        // Business logic:
        // 1. Basic validation of numeric ranges that complement entity.isValid() checks.
        // 2. Ensure the referenced Location exists and is active; if not, mark the observation as not processed and log.
        // Note: We must not perform update operations on this entity via EntityService. Mutating the entity instance
        // is sufficient; Cyoda will persist it according to the workflow.

        // Additional numeric sanity checks (defensive)
        try {
            if (entity.getTemperature() != null) {
                double t = entity.getTemperature();
                if (Double.isNaN(t) || Double.isInfinite(t) || t < -100.0 || t > 80.0) {
                    logger.warn("Temperature out of plausible bounds for observation {}: {}", entity.getObservationId(), t);
                    // keep entity as-is; validation phase already passed; flagging by setting processed=false
                    entity.setProcessed(false);
                    return entity;
                }
            }

            if (entity.getHumidity() != null) {
                int h = entity.getHumidity();
                if (h < 0 || h > 100) {
                    logger.warn("Humidity out of bounds for observation {}: {}", entity.getObservationId(), h);
                    entity.setProcessed(false);
                    return entity;
                }
            }

            if (entity.getWindSpeed() != null) {
                double w = entity.getWindSpeed();
                if (Double.isNaN(w) || Double.isInfinite(w) || w < 0.0) {
                    logger.warn("WindSpeed invalid for observation {}: {}", entity.getObservationId(), w);
                    entity.setProcessed(false);
                    return entity;
                }
            }

            if (entity.getPrecipitation() != null) {
                double p = entity.getPrecipitation();
                if (Double.isNaN(p) || Double.isInfinite(p) || p < 0.0) {
                    logger.warn("Precipitation invalid for observation {}: {}", entity.getObservationId(), p);
                    entity.setProcessed(false);
                    return entity;
                }
            }
        } catch (Exception ex) {
            logger.error("Error during numeric validation for observation {}: {}", entity.getObservationId(), ex.getMessage(), ex);
            entity.setProcessed(false);
            return entity;
        }

        // Verify referenced Location exists and is active (if not, mark as not processed)
        if (entity.getLocationId() != null && !entity.getLocationId().isBlank()) {
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.locationId", "EQUALS", entity.getLocationId())
            );

            try {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Location.ENTITY_NAME,
                    String.valueOf(Location.ENTITY_VERSION),
                    condition,
                    true
                );

                ArrayNode results = itemsFuture.get();
                if (results == null || results.size() == 0) {
                    logger.warn("Referenced location not found for observation {}: {}", entity.getObservationId(), entity.getLocationId());
                    entity.setProcessed(false);
                    return entity;
                } else {
                    JsonNode loc = results.get(0);
                    boolean active = loc.path("active").asBoolean(false);
                    if (!active) {
                        logger.warn("Referenced location is not active for observation {}: {}", entity.getObservationId(), entity.getLocationId());
                        entity.setProcessed(false);
                        return entity;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while fetching location for observation {}: {}", entity.getObservationId(), ie.getMessage(), ie);
                entity.setProcessed(false);
                return entity;
            } catch (ExecutionException ee) {
                logger.error("Execution error while fetching location for observation {}: {}", entity.getObservationId(), ee.getMessage(), ee);
                entity.setProcessed(false);
                return entity;
            } catch (Exception e) {
                logger.error("Unexpected error while validating location for observation {}: {}", entity.getObservationId(), e.getMessage(), e);
                entity.setProcessed(false);
                return entity;
            }
        } else {
            logger.warn("Observation {} missing locationId", entity.getObservationId());
            entity.setProcessed(false);
            return entity;
        }

        // If all checks pass, leave processed as-is (validation successful).
        // Optionally, we can ensure processed is explicitly false (it will be set true later during enrichment).
        if (entity.getProcessed() == null) {
            // Ensure processed is explicitly set to false after validation (enrichment step will set true)
            entity.setProcessed(false);
        }

        logger.info("Observation {} validated successfully", entity.getObservationId());
        return entity;
    }
}