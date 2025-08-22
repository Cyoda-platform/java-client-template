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
        if (entity == null) return null;

        try {
            String currentStatus = entity.getStatus();
            if (currentStatus == null) {
                // If status is missing, we don't change it automatically to avoid unintended transitions.
                logger.warn("User {} has null status, skipping verification flow", entity.getId());
                return entity;
            }

            // If user is Active, initiate verification flow -> move to VERIFICATION_PENDING
            if ("Active".equalsIgnoreCase(currentStatus.trim())) {
                entity.setStatus("VERIFICATION_PENDING");

                // Prefer email, fallback to phone. We only log scheduling here; actual delivery is out-of-scope.
                if (entity.getEmail() != null && !entity.getEmail().isBlank()) {
                    logger.info("User {} marked VERIFICATION_PENDING - scheduling verification email to {}", entity.getId(), entity.getEmail());
                } else if (entity.getPhone() != null && !entity.getPhone().isBlank()) {
                    logger.info("User {} marked VERIFICATION_PENDING - scheduling verification SMS to {}", entity.getId(), entity.getPhone());
                } else {
                    logger.warn("User {} marked VERIFICATION_PENDING but no contact info (email/phone) available", entity.getId());
                }
            } else {
                // For other statuses (VERIFICATION_PENDING, Inactive, etc.) we do not change state here
                logger.debug("User {} status is '{}', no verification action taken", entity.getId(), currentStatus);
            }
        } catch (Exception ex) {
            logger.error("Unexpected error in UserVerificationProcessor for user {}: {}", entity.getId(), ex.getMessage(), ex);
        }

        return entity;
    }
}