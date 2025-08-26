package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
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

import java.time.Instant;

@Component
public class ActivateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ActivateUserProcessor(SerializerFactory serializerFactory) {
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

        // Business logic for activation step:
        // - Normalize email to lowercase
        // - Ensure subscribed is not null (default to false)
        // - If role indicates a pre-activation state (e.g., "registered" or "pending"), set to "regular"
        // - Update updatedAt timestamp to current time
        // Note: Do not call entityService.updateItem on this entity. Cyoda will persist changes automatically.

        try {
            // Normalize email
            if (entity.getEmail() != null) {
                entity.setEmail(entity.getEmail().trim().toLowerCase());
            }

            // Ensure subscribed is not null
            if (entity.getSubscribed() == null) {
                entity.setSubscribed(Boolean.FALSE);
            }

            // Normalize and adjust role to active state if needed
            String role = entity.getRole();
            if (role != null) {
                String normalizedRole = role.trim();
                if ("registered".equalsIgnoreCase(normalizedRole) || "pending".equalsIgnoreCase(normalizedRole)) {
                    entity.setRole("regular");
                } else {
                    // keep existing role (e.g., "admin" or "regular")
                    entity.setRole(normalizedRole);
                }
            } else {
                // Defensive: if somehow role is null (shouldn't happen after validation), set to "regular"
                entity.setRole("regular");
            }

            // Set updatedAt to current ISO timestamp
            entity.setUpdatedAt(Instant.now().toString());

            logger.info("Activated user {} with role={} subscribed={}", entity.getUserId(), entity.getRole(), entity.getSubscribed());
        } catch (Exception ex) {
            logger.error("Error while activating user {}: {}", entity != null ? entity.getUserId() : "unknown", ex.getMessage(), ex);
            // Do not throw; let validation/processing pipeline handle any further action.
        }

        return entity;
    }
}