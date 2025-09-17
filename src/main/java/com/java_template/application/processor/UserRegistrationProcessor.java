package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UserRegistrationProcessor - Registers a new user in the system
 * 
 * Transition: register_user (none → registered)
 * Purpose: Registers new user with encrypted password and default values
 */
@Component
public class UserRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserRegistrationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UserRegistrationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User registration for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(User.class)
                .validate(this::isValidEntityWithMetadata, "Invalid user entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<User> entityWithMetadata) {
        User entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic processing method
     * Registers user with encrypted password and default values
     */
    private EntityWithMetadata<User> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<User> context) {

        EntityWithMetadata<User> entityWithMetadata = context.entityResponse();
        User user = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing user registration: {} in state: {}", user.getUsername(), currentState);

        // Set default values
        user.setRegistrationDate(LocalDateTime.now());
        user.setIsActive(false); // User starts as inactive until verified

        // Generate unique userId if not provided
        if (user.getUserId() == null || user.getUserId().trim().isEmpty()) {
            user.setUserId("user-" + UUID.randomUUID().toString().substring(0, 8));
        }

        // Encrypt password (simple hash for demo - in real app use proper encryption)
        if (user.getPassword() != null) {
            user.setPassword(encryptPassword(user.getPassword()));
        }

        logger.info("User {} registered successfully", user.getUsername());

        return entityWithMetadata;
    }

    /**
     * Simple password encryption (in real app, use proper encryption)
     */
    private String encryptPassword(String password) {
        return "encrypted_" + password.hashCode();
    }
}
