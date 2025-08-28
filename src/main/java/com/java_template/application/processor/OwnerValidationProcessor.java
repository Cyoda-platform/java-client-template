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

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class OwnerValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OwnerValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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

        // Normalize email and phone
        if (entity.getEmail() != null) {
            entity.setEmail(entity.getEmail().trim());
        }
        if (entity.getPhone() != null) {
            entity.setPhone(entity.getPhone().trim());
        }

        boolean emailValid = isValidEmail(entity.getEmail());
        boolean phoneValid = isValidPhone(entity.getPhone());

        // If both contact methods are valid, ensure role is at least "owner"
        if (emailValid && phoneValid) {
            // Promote to owner role if currently a visitor or blank
            String currentRole = entity.getRole() == null ? "" : entity.getRole().trim().toLowerCase();
            if ("visitor".equals(currentRole) || currentRole.isBlank()) {
                entity.setRole("owner");
            }
            // update timestamp
            entity.setUpdatedAt(Instant.now());
            logger.info("Owner {} contact validated (email={}, phone={})", entity.getId(), emailValid, phoneValid);
        } else {
            // If contacts are invalid, mark as visitor role so that downstream flows can handle verification/manual fix
            entity.setRole("visitor");
            // annotate bio with validation note (preserve existing bio)
            String note = " Contact validation failed:";
            if (!emailValid) note += " invalid_email";
            if (!phoneValid) note += " invalid_phone";
            String existingBio = entity.getBio() == null ? "" : entity.getBio();
            // Avoid unbounded growth: if note already present, do not append repeatedly
            if (!existingBio.contains(note.trim())) {
                entity.setBio((existingBio + " " + note).trim());
            }
            entity.setUpdatedAt(Instant.now());
            logger.warn("Owner {} failed contact validation (emailValid={}, phoneValid={})", entity.getId(), emailValid, phoneValid);
        }

        return entity;
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        // Simple email pattern - sufficient for basic validation
        return email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    private boolean isValidPhone(String phone) {
        if (phone == null || phone.isBlank()) return false;
        // Allow digits, spaces, dashes, parentheses and leading +
        String normalized = phone.replaceAll("[\\s\\-()]+", "");
        if (normalized.startsWith("+")) {
            normalized = normalized.substring(1);
        }
        // Must be all digits now and reasonable length (7-15)
        if (!normalized.matches("\\d+")) return false;
        int len = normalized.length();
        return len >= 7 && len <= 15;
    }
}