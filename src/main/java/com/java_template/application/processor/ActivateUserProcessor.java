package com.java_template.application.processor;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ActivateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ActivateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final String PROFILE_VERIFIED = "PROFILE_VERIFIED";
    private static final String ACTIVE = "ACTIVE";

    public ActivateUserProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
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

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User entity = context.entity();
        if (entity == null) {
            logger.warn("ActivateUserProcessor received null entity in context");
            return null;
        }

        String currentStatus = null;
        try {
            currentStatus = entity.getStatus();
        } catch (Exception e) {
            logger.warn("ActivateUserProcessor: failed to read current status for user (userId={}) : {}", entity.getUserId(), e.getMessage());
        }

        if (currentStatus == null) {
            logger.warn("ActivateUserProcessor: user {} has null status, skipping activation", entity.getUserId());
            return entity;
        }

        // Only transition users that are in PROFILE_VERIFIED state to ACTIVE.
        if (PROFILE_VERIFIED.equalsIgnoreCase(currentStatus)) {
            // Basic identity sanity check: ensure email contains '@' as minimal verification guard.
            String email = entity.getEmail();
            if (email != null && email.contains("@")) {
                entity.setStatus(ACTIVE);
                logger.info("User {} transitioned from PROFILE_VERIFIED to {}", entity.getUserId(), ACTIVE);
            } else {
                // If email not valid, keep as PROFILE_VERIFIED and log for further review.
                logger.warn("ActivateUserProcessor: user {} profile verified but email appears invalid ({}). Activation skipped.", entity.getUserId(), email);
            }
        } else if (ACTIVE.equalsIgnoreCase(currentStatus)) {
            // Already active, nothing to do.
            logger.debug("ActivateUserProcessor: user {} already {}", entity.getUserId(), ACTIVE);
        } else {
            // Not in expected state for activation; do not change.
            logger.debug("ActivateUserProcessor skipped for user {} with status {}", entity.getUserId(), currentStatus);
        }

        return entity;
    }
}