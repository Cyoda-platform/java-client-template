package com.java_template.application.processor;

import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Laureate.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate entity) {
        return entity != null && entity.isValid();
    }

    private Laureate processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Laureate> context) {
        Laureate entity = context.entity();
        try {
            logger.debug("Validating Laureate id={}", entity != null ? entity.getId() : null);

            // Re-check required fields defensively (validate() already enforces this)
            if (entity == null) {
                logger.warn("Laureate entity is null in processing context");
                return null;
            }
            if (entity.getId() == null
                || entity.getFirstname() == null || entity.getFirstname().isBlank()
                || entity.getYear() == null || entity.getYear().isBlank()
                || entity.getCategory() == null || entity.getCategory().isBlank()) {
                // Shouldn't reach here because of serializer.validate, but handle defensively
                logger.warn("Laureate failed validation checks: id={}, firstname={}, year={}, category={}",
                        entity.getId(), entity.getFirstname(), entity.getYear(), entity.getCategory());
                // No dedicated state field exists on Laureate; avoid creating new properties.
                // We keep the entity unchanged so downstream processors/criteria can handle failure via isValid checks/logging.
                return entity;
            }

            // Minimal post-validation action: normalize simple country code if present.
            // (EnrichmentProcessor will perform more advanced enrichment later.)
            String countryCode = entity.getBorncountrycode();
            if (countryCode != null && !countryCode.isBlank()) {
                entity.setNormalizedCountryCode(countryCode.trim().toUpperCase());
            }

            logger.info("Laureate validated successfully: id={}, firstname={}, year={}, category={}",
                    entity.getId(), entity.getFirstname(), entity.getYear(), entity.getCategory());

            return entity;
        } catch (Exception ex) {
            logger.error("Unexpected error during ValidationProcessor for Laureate id={}: {}", 
                    entity != null ? entity.getId() : null, ex.getMessage(), ex);
            // On unexpected error, return entity unchanged so error handling upstream can record it
            return entity;
        }
    }
}