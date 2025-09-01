package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

@Component
public class OwnerValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    public OwnerValidationProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
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

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner entity = context.entity();

        if (entity == null) return null;

        // Normalize name
        if (entity.getName() != null) {
            String name = entity.getName().trim();
            entity.setName(name.isBlank() ? null : name);
        }

        // Normalize and validate contactEmail
        if (entity.getContactEmail() != null) {
            String email = entity.getContactEmail().trim().toLowerCase();
            if (email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
                logger.warn("Owner [{}] has invalid contactEmail '{}', clearing it.", entity.getId(), entity.getContactEmail());
                entity.setContactEmail(null);
            } else {
                entity.setContactEmail(email);
            }
        }

        // Normalize phone: remove non-digits, ensure reasonable length
        if (entity.getPhone() != null) {
            String digits = entity.getPhone().replaceAll("\\D+", "");
            if (digits.length() < 7) {
                logger.warn("Owner [{}] has invalid phone '{}', clearing it.", entity.getId(), entity.getPhone());
                entity.setPhone(null);
            } else {
                entity.setPhone(digits);
            }
        }

        // Normalize address
        if (entity.getAddress() != null) {
            String addr = entity.getAddress().trim();
            entity.setAddress(addr.isBlank() ? null : addr);
        }

        // Validate preferences JSON if present; store compacted JSON string
        if (entity.getPreferences() != null) {
            String pref = entity.getPreferences().trim();
            if (pref.isBlank()) {
                entity.setPreferences(null);
            } else {
                try {
                    JsonNode node = objectMapper.readTree(pref);
                    String compact = objectMapper.writeValueAsString(node);
                    entity.setPreferences(compact);
                } catch (Exception e) {
                    logger.warn("Owner [{}] has invalid preferences JSON, clearing it. Error: {}", entity.getId(), e.getMessage());
                    entity.setPreferences(null);
                }
            }
        }

        return entity;
    }
}