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
 * UserReactivationProcessor - Reactivates a user account
 * 
 * Transitions: reactivate_user (suspended → active), 
 *              reactivate_inactive (inactive → active)
 * Purpose: Reactivates user account
 */
@Component
public class UserReactivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserReactivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UserReactivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User reactivation for request: {}", request.getId());

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
        return entity != null && entity.isValid() && technicalId != null && 
               ("suspended".equals(currentState) || "inactive".equals(currentState));
    }

    /**
     * Main business logic processing method
     * Reactivates user account
     */
    private EntityWithMetadata<User> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<User> context) {

        EntityWithMetadata<User> entityWithMetadata = context.entityResponse();
        User user = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing user reactivation: {} in state: {}", user.getUsername(), currentState);

        // Reactivate user
        user.setIsActive(true);
        user.setLastLoginDate(LocalDateTime.now());

        logger.info("User {} reactivated successfully", user.getUsername());

        return entityWithMetadata;
    }
}
