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

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
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

        // Business logic:
        // - If a freshly active user requires verification, move them to VERIFICATION_PENDING and "send" verification
        // - If contact details available, log the intent to send verification via email or SMS
        // - Do not perform external calls here; Cyoda will persist the modified entity state automatically

        if (entity == null) {
            logger.warn("Received null User entity in processing context");
            return entity;
        }

        String currentStatus = entity.getStatus();
        if (currentStatus == null) {
            logger.warn("User {} has null status, skipping verification flow", entity.getId());
            return entity;
        }

        // If user is Active, trigger verification flow by marking as VERIFICATION_PENDING
        if ("Active".equalsIgnoreCase(currentStatus)) {
            entity.setStatus("VERIFICATION_PENDING");
            if (entity.getEmail() != null && !entity.getEmail().isBlank()) {
                logger.info("User {} marked VERIFICATION_PENDING - scheduling verification email to {}", entity.getId(), entity.getEmail());
            } else if (entity.getPhone() != null && !entity.getPhone().isBlank()) {
                logger.info("User {} marked VERIFICATION_PENDING - scheduling verification SMS to {}", entity.getId(), entity.getPhone());
            } else {
                logger.warn("User {} marked VERIFICATION_PENDING but no contact info (email/phone) available", entity.getId());
            }
        } else {
            logger.debug("User {} status is '{}', no verification action taken", entity.getId(), currentStatus);
        }

        return entity;
    }
}