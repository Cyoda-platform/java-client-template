package com.java_template.application.processor;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class ValidateAndEnrichProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateAndEnrichProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateAndEnrichProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User entity = context.entity();

        try {
            // Normalize and enrich email
            if (entity.getEmail() != null) {
                String email = entity.getEmail().trim().toLowerCase();
                entity.setEmail(email);
                if (!isPlausibleEmail(email)) {
                    logger.warn("User {} has potentially invalid email: {}", entity.getId(), email);
                }
            }

            // Trim and normalize names
            if (entity.getFirstName() != null) {
                entity.setFirstName(capitalize(entity.getFirstName().trim()));
            }
            if (entity.getLastName() != null) {
                entity.setLastName(capitalize(entity.getLastName().trim()));
            }

            // Ensure source is set; SaveUserProcessor is expected to set this but keep safe guard
            if (entity.getSource() == null || entity.getSource().isBlank()) {
                entity.setSource("ReqRes");
            }

            // Ensure retrievedAt is set
            if (entity.getRetrievedAt() == null || entity.getRetrievedAt().isBlank()) {
                entity.setRetrievedAt(Instant.now().toString());
            }

            // Avatar normalization: trim if present
            if (entity.getAvatar() != null) {
                entity.setAvatar(entity.getAvatar().trim());
            }

        } catch (Exception ex) {
            logger.error("Error while validating/enriching User {}: {}", entity != null ? entity.getId() : "unknown", ex.getMessage(), ex);
        }

        // Important: do not call entityService.updateItem on this entity. Mutating the entity is sufficient;
        // Cyoda will persist the changes automatically as part of the workflow.
        return entity;
    }

    // Simple helpers

    private boolean isPlausibleEmail(String email) {
        if (email == null) return false;
        int at = email.indexOf('@');
        int dot = email.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < email.length() - 1;
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) return value;
        String trimmed = value.trim();
        if (trimmed.length() == 1) return trimmed.toUpperCase();
        return trimmed.substring(0,1).toUpperCase() + trimmed.substring(1);
    }
}