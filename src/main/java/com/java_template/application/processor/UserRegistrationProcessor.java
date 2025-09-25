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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * UserRegistrationProcessor - Handles user registration logic
 * Processes new user registrations and sets initial values
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
                .map(this::processUserRegistrationLogic)
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
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Main business logic for user registration
     */
    private EntityWithMetadata<User> processUserRegistrationLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<User> context) {

        EntityWithMetadata<User> entityWithMetadata = context.entityResponse();
        User user = entityWithMetadata.entity();

        // Get current entity metadata
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing user registration: {} in state: {}", user.getEmail(), currentState);

        // Set registration timestamp if not already set
        if (user.getRegistrationDate() == null) {
            user.setRegistrationDate(LocalDateTime.now());
        }

        // Set default active status if not specified
        if (user.getIsActive() == null) {
            user.setIsActive(false); // Users start inactive until activated
        }

        // Validate role assignment
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole("EXTERNAL_SUBMITTER"); // Default role
        }

        logger.info("User {} registered successfully with role: {}", user.getEmail(), user.getRole());

        return entityWithMetadata;
    }
}
