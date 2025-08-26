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
public class TagLocationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TagLocationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TagLocationProcessor(SerializerFactory serializerFactory) {
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

        // Normalize identifiers and names
        try {
            if (entity.getLocationId() != null) {
                entity.setLocationId(entity.getLocationId().trim().toUpperCase());
            }
        } catch (Exception ex) {
            logger.debug("Failed to normalize locationId: {}", ex.getMessage());
        }

        try {
            if (entity.getName() != null) {
                entity.setName(entity.getName().trim());
            }
        } catch (Exception ex) {
            logger.debug("Failed to normalize name: {}", ex.getMessage());
        }

        // Ensure active flag is set; if not, default to true for validated locations
        try {
            if (entity.getActive() == null) {
                entity.setActive(Boolean.TRUE);
            }
        } catch (Exception ex) {
            logger.debug("Failed to set active flag: {}", ex.getMessage());
        }

        // Tag region if missing using simple hemisphere heuristic
        try {
            if (entity.getRegion() == null || entity.getRegion().isBlank()) {
                Double lat = entity.getLatitude();
                String regionTag = "Unknown";
                if (lat != null) {
                    if (lat > 0.0) regionTag = "Northern Hemisphere";
                    else if (lat < 0.0) regionTag = "Southern Hemisphere";
                    else regionTag = "Equator";
                }
                entity.setRegion(regionTag);
            }
        } catch (Exception ex) {
            logger.debug("Failed to determine region tag: {}", ex.getMessage());
        }

        // Tag timezone if missing using longitude -> approximate GMT offset
        try {
            if (entity.getTimezone() == null || entity.getTimezone().isBlank()) {
                Double lon = entity.getLongitude();
                String tz = "UTC";
                if (lon != null) {
                    // Approximate timezone by 15-degree longitudinal zones
                    int offset = (int) Math.round(lon / 15.0);
                    // Format as GMT+/-N
                    if (offset >= 0) tz = "GMT+" + offset;
                    else tz = "GMT" + offset; // offset negative already includes '-'
                }
                entity.setTimezone(tz);
            }
        } catch (Exception ex) {
            logger.debug("Failed to determine timezone tag: {}", ex.getMessage());
        }

        logger.debug("Tagged Location [{}] -> region: {}, timezone: {}, active: {}",
            entity.getLocationId(), entity.getRegion(), entity.getTimezone(), entity.getActive());

        return entity;
    }
}