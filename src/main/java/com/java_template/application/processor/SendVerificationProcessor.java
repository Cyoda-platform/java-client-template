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

@Component
public class SendVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SendVerificationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Business logic:
        // - Ensure user has an email to send verification to. If missing, log and return.
        // - If verified flag is null, explicitly set it to false (unverified).
        // - If user is already verified, do nothing except log.
        // - Otherwise, simulate sending verification by logging. The actual persistence of the entity
        //   (with any state changes) is handled by Cyoda automatically after this processor returns.

        if (entity == null) {
            logger.warn("User entity is null in SendVerificationProcessor");
            return entity;
        }

        if (entity.getEmail() == null || entity.getEmail().isBlank()) {
            logger.warn("User {} has no email, cannot send verification", entity.getId());
            // Ensure verified is non-null as required by isValid() even if email missing
            if (entity.getVerified() == null) {
                entity.setVerified(Boolean.FALSE);
            }
            return entity;
        }

        // Ensure verified flag is non-null
        if (entity.getVerified() == null) {
            entity.setVerified(Boolean.FALSE);
        }

        if (Boolean.TRUE.equals(entity.getVerified())) {
            logger.info("User {} is already verified, no verification email sent", entity.getId());
            return entity;
        }

        // Simulate sending verification email (actual sending should be done by an external service)
        logger.info("Sending verification email to user {} at {}", entity.getId(), entity.getEmail());

        // Keep user as unverified until external confirmation arrives.
        entity.setVerified(Boolean.FALSE);

        return entity;
    }
}