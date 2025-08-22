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
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateUserProcessor(SerializerFactory serializerFactory) {
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

        // Normalize and trim textual fields
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity.getEmail() != null) {
            entity.setEmail(entity.getEmail().trim().toLowerCase());
        }
        if (entity.getPhone() != null) {
            entity.setPhone(entity.getPhone().trim());
        }
        if (entity.getAddress() != null) {
            entity.setAddress(entity.getAddress().trim());
        }

        // Ensure verified flag is non-null (isValid already asserts this, but guard defensively)
        if (entity.getVerified() == null) {
            entity.setVerified(false);
        }

        // Normalize role: allow only "customer" or "staff", default to "customer"
        if (entity.getRole() == null || entity.getRole().isBlank()) {
            entity.setRole("customer");
        } else {
            String roleLower = entity.getRole().trim().toLowerCase();
            if (!"customer".equals(roleLower) && !"staff".equals(roleLower)) {
                logger.warn("Unknown role '{}' for user id {}. Defaulting to 'customer'.", entity.getRole(), entity.getId());
                entity.setRole("customer");
            } else {
                entity.setRole(roleLower);
            }
        }

        // Basic email format validation: if invalid, mark as unverified and log
        if (entity.getEmail() != null && !isValidEmail(entity.getEmail())) {
            logger.warn("Invalid email format for user id {}: '{}'. Marking as unverified.", entity.getId(), entity.getEmail());
            entity.setVerified(false);
        }

        // No updates to other entities here. The current user entity will be persisted by Cyoda automatically.
        return entity;
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        int at = email.indexOf('@');
        if (at <= 0) return false;
        int dot = email.indexOf('.', at);
        return dot > at + 1 && dot < email.length() - 1;
    }
}