package com.java_template.application.processor;

import com.java_template.application.entity.location.version_1.Location;
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

@Component
public class ValidateLocationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateLocationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateLocationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Location for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Location.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Location entity) {
        return entity != null && entity.isValid();
    }

    private Location processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Location> context) {
        Location entity = context.entity();

        // Business rule:
        // If coordinates and timezone are valid, mark location as active (monitoring enabled).
        // Otherwise mark location as inactive (requires manual fix / invalid).
        try {
            Double lat = entity.getLatitude();
            Double lon = entity.getLongitude();
            String tz = entity.getTimezone();

            boolean coordsValid = lat != null && lon != null && lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0;
            boolean timezoneValid = tz != null && !tz.isBlank();

            if (coordsValid && timezoneValid) {
                if (entity.getActive() == null || !entity.getActive()) {
                    logger.info("Location {} validated successfully - activating", entity.getLocationId());
                }
                entity.setActive(true);
            } else {
                if (entity.getActive() == null || entity.getActive()) {
                    logger.warn("Location {} failed validation - deactivating (coordsValid={}, timezoneValid={})",
                        entity.getLocationId(), coordsValid, timezoneValid);
                }
                entity.setActive(false);
            }
        } catch (Exception ex) {
            logger.error("Error while validating Location {}: {}", entity != null ? entity.getLocationId() : "unknown", ex.getMessage(), ex);
            // On unexpected error, mark as inactive to prevent accidental activation
            if (entity != null) {
                entity.setActive(false);
            }
        }

        return entity;
    }
}