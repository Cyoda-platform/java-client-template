package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

@Component
public class ApplyPreferencesProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ApplyPreferencesProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String STATUS_PREFERENCES_APPLIED = "PREFERENCES_APPLIED";
    private static final String STATUS_PREFERENCES_INVALID = "PREFERENCES_INVALID";

    public ApplyPreferencesProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();

        try {
            // Normalize email and name
            if (subscriber.getEmail() != null) {
                subscriber.setEmail(subscriber.getEmail().trim().toLowerCase());
            }
            if (subscriber.getName() != null) {
                subscriber.setName(subscriber.getName().trim());
            }
            if (subscriber.getFilters() != null) {
                subscriber.setFilters(subscriber.getFilters().trim());
            }
            if (subscriber.getFrequency() != null) {
                subscriber.setFrequency(subscriber.getFrequency().trim().toLowerCase());
            }

            boolean emailValid = subscriber.getEmail() != null && EMAIL_PATTERN.matcher(subscriber.getEmail()).matches();

            boolean frequencyValid = false;
            if (subscriber.getFrequency() != null) {
                String freq = subscriber.getFrequency().toLowerCase();
                frequencyValid = "daily".equals(freq) || "weekly".equals(freq) || "on_change".equals(freq) || "on-change".equals(freq);
            }

            // Apply preferences only if both email and frequency are valid
            if (emailValid && frequencyValid) {
                subscriber.setStatus(STATUS_PREFERENCES_APPLIED);
                logger.info("Subscriber {} preferences applied (email valid: {}, frequency valid: {})", subscriber.getSubscriberId(), emailValid, frequencyValid);
            } else {
                // Mark as invalid preferences so downstream processors/criteria can act upon it
                subscriber.setStatus(STATUS_PREFERENCES_INVALID);
                logger.warn("Subscriber {} preferences invalid (emailValid={}, frequencyValid={})", subscriber.getSubscriberId(), emailValid, frequencyValid);
            }

        } catch (Exception ex) {
            logger.error("Error while applying preferences for subscriber {}: {}", subscriber != null ? subscriber.getSubscriberId() : "unknown", ex.getMessage(), ex);
            // On unexpected error, mark preferences invalid to avoid accidental enabling
            if (subscriber != null) {
                subscriber.setStatus(STATUS_PREFERENCES_INVALID);
            }
        }

        return subscriber;
    }
}