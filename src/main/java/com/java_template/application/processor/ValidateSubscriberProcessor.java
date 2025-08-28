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
public class ValidateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @Autowired
    public ValidateSubscriberProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
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

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();

        // Normalize email and name
        if (entity.getEmail() != null) {
            String normalizedEmail = entity.getEmail().trim().toLowerCase();
            entity.setEmail(normalizedEmail);
        }

        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }

        // Normalize filters if present
        if (entity.getFilters() != null) {
            entity.setFilters(entity.getFilters().trim());
        }

        // Normalize frequency to lower-case if present, validate allowed values
        if (entity.getFrequency() != null && !entity.getFrequency().isBlank()) {
            String freqTrim = entity.getFrequency().trim().toLowerCase();
            if (!"daily".equals(freqTrim) && !"weekly".equals(freqTrim) && !"on_change".equals(freqTrim)) {
                logger.warn("Subscriber {} has invalid frequency '{}'. Defaulting to 'weekly'.",
                        entity.getSubscriberId(), entity.getFrequency());
                entity.setFrequency("weekly");
            } else {
                entity.setFrequency(freqTrim);
            }
        } else {
            // default frequency if missing
            entity.setFrequency("weekly");
        }

        // Validate email format. If invalid, mark subscriber as UNSUBSCRIBED to avoid sending notifications.
        boolean emailValid = false;
        if (entity.getEmail() != null && !entity.getEmail().isBlank()) {
            emailValid = EMAIL_PATTERN.matcher(entity.getEmail()).matches();
        }

        if (!emailValid) {
            logger.warn("Subscriber {} has invalid email '{}'. Setting status to UNSUBSCRIBED.", entity.getSubscriberId(), entity.getEmail());
            entity.setStatus("UNSUBSCRIBED");
        } else {
            // If email valid, ensure status normalization for consistency
            if (entity.getStatus() != null) {
                entity.setStatus(entity.getStatus().trim().toUpperCase());
            }
            // If status is missing, default to ACTIVE
            if (entity.getStatus() == null || entity.getStatus().isBlank()) {
                entity.setStatus("ACTIVE");
            }
        }

        // Ensure status is in expected uppercase form regardless of branch
        if (entity.getStatus() != null) {
            entity.setStatus(entity.getStatus().trim().toUpperCase());
        }

        // No external entity modifications are performed here on the triggering entity.
        // Any other entity operations (add/update/delete) would use entityService, but are not required for validation.

        return entity;
    }
}