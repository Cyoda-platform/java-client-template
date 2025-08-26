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
public class UserVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public UserVerificationProcessor(SerializerFactory serializerFactory) {
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
        if (entity == null) {
            logger.warn("User entity is null in execution context");
            return null;
        }

        String currentStatus = entity.getStatus();
        String email = entity.getEmail();

        // Do not modify suspended users
        if (currentStatus != null && currentStatus.equalsIgnoreCase("suspended")) {
            logger.info("User {} is suspended; skipping verification", entity.getId());
            return entity;
        }

        boolean emailValid = isEmailValid(email);

        // If already verified, keep as is
        if (currentStatus != null && currentStatus.equalsIgnoreCase("verified")) {
            logger.info("User {} already verified", entity.getId());
            return entity;
        }

        if (emailValid) {
            entity.setStatus("verified");
            logger.info("User {} marked as verified", entity.getId());
        } else {
            // Keep users without valid email in 'new' status to allow manual verification later
            entity.setStatus("new");
            logger.info("User {} left as new due to invalid or missing email", entity.getId());
        }

        return entity;
    }

    private boolean isEmailValid(String email) {
        if (email == null) return false;
        email = email.trim();
        if (email.isEmpty()) return false;
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) return false;
        String domain = email.substring(atIndex + 1);
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".");
    }
}