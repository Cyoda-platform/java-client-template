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
 * UserActivationProcessor - Activates a registered user account
 * 
 * Transition: activate_user (registered → active)
 * Purpose: Activates user account after verification
 */
@Component
public class UserActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UserActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User activation for request: {}", request.getId());

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
        String currentState = entityWithMetadata.metadata().getState();
        return entity != null && entity.isValid() && technicalId != null && "registered".equals(currentState);
    }

    /**
     * Main business logic processing method
     * Activates user account
     */
    private EntityWithMetadata<User> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<User> context) {

        EntityWithMetadata<User> entityWithMetadata = context.entityResponse();
        User user = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing user activation: {} in state: {}", user.getUsername(), currentState);

        // Activate user
        user.setIsActive(true);
        user.setLastLoginDate(LocalDateTime.now());

        logger.info("User {} activated successfully", user.getUsername());

        return entityWithMetadata;
    }
}
