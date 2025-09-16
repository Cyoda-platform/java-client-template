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
 * UserActivationProcessor - Activate user account and set defaults
 * 
 * Transition: activate_user (none → active)
 * Purpose: Activate user account and set defaults
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

    private boolean isValidEntityWithMetadata(EntityWithMetadata<User> entityWithMetadata) {
        User entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<User> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<User> context) {

        EntityWithMetadata<User> entityWithMetadata = context.entityResponse();
        User user = entityWithMetadata.entity();

        logger.debug("Processing user activation: {}", user.getUserId());

        // 1. Validate user has required fields - already done in isValid()
        // 2. Validate email format is correct - already done in isValid()
        
        // 3. Set default values
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        
        // Set default preferences if not specified
        if (user.getPreferences() == null) {
            user.setPreferences(new User.UserPreferences());
        }
        if (user.getPreferences().getNewsletter() == null) {
            user.getPreferences().setNewsletter(true);
        }
        if (user.getPreferences().getNotifications() == null) {
            user.getPreferences().setNotifications(true);
        }

        // 4. Set updatedAt = current timestamp
        user.setUpdatedAt(LocalDateTime.now());

        logger.info("User {} activated successfully", user.getUserId());
        return entityWithMetadata;
    }
}
