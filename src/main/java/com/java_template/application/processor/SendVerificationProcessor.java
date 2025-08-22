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

        // Business logic for sending verification:
        // - If no email present -> log warning, ensure verified flag is not null (set to false) and do nothing else.
        // - If verified is null -> initialize to FALSE.
        // - If already verified -> log info and skip sending.
        // - Otherwise "send" verification (here: log the intent) and keep verified=false (actual verification happens via separate flow).
        if (entity == null) {
            logger.warn("SendVerificationProcessor received null entity in context");
            return null;
        }

        String email = entity.getEmail();
        if (email == null || email.isBlank()) {
            logger.warn("User {} has no email, cannot send verification", entity.getId());
            if (entity.getVerified() == null) {
                entity.setVerified(Boolean.FALSE);
            }
            return entity;
        }

        // Normalize verified flag if missing
        if (entity.getVerified() == null) {
            entity.setVerified(Boolean.FALSE);
        }

        if (Boolean.TRUE.equals(entity.getVerified())) {
            logger.info("User {} is already verified, no verification email sent", entity.getId());
            return entity;
        }

        // "Send" verification (no external calls allowed here). The actual delivery would be handled by infrastructure.
        logger.info("Sending verification email to user {} at {}", entity.getId(), email);

        // Keep verified = false until user confirms via separate flow/criterion.
        entity.setVerified(Boolean.FALSE);

        return entity;
    }
}