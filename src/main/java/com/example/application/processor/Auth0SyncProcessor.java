package com.example.application.processor;

import com.example.application.entity.user.version_1.User;
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

import java.time.OffsetDateTime;

/**
 * Auth0SyncProcessor - Handles Auth0 user synchronization
 * 
 * This processor manages the manual sync of users from Auth0 into the local store.
 * It updates user metadata and tracks sync status.
 */
@Component
public class Auth0SyncProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(Auth0SyncProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public Auth0SyncProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Auth0 sync for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(User.class)
                .validate(this::isValidEntityWithMetadata, "Invalid user entity")
                .map(this::processAuth0SyncLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<User> entityWithMetadata) {
        User user = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return user != null && user.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    private EntityWithMetadata<User> processAuth0SyncLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<User> context) {

        EntityWithMetadata<User> entityWithMetadata = context.entityResponse();
        User user = entityWithMetadata.entity();

        logger.debug("Syncing user: {} from Auth0", user.getUserId());

        // Update sync timestamp
        user.setLastSyncTime(OffsetDateTime.now());
        user.setPresentInAuth0(true);

        logger.info("User {} synced successfully from Auth0", user.getUserId());

        return entityWithMetadata;
    }
}

