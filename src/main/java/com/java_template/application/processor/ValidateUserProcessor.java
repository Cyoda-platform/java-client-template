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

import java.util.regex.Pattern;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        Pattern.CASE_INSENSITIVE
    );

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

        if (entity == null) {
            return null;
        }

        // Normalize textual fields: trim spaces
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

        // Ensure verified is non-null
        if (entity.getVerified() == null) {
            entity.setVerified(Boolean.FALSE);
        }

        // Normalize and validate role
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

        // Validate email format if present
        if (entity.getEmail() != null && !entity.getEmail().isBlank()) {
            if (!isValidEmail(entity.getEmail())) {
                logger.warn("Invalid email format for user id {}: '{}'. Marking as unverified.", entity.getId(), entity.getEmail());
                entity.setVerified(Boolean.FALSE);
            }
        }

        logger.info("User {} processed: name='{}', email='{}', verified={}", entity.getId(), entity.getName(), entity.getEmail(), entity.getVerified());

        return entity;
    }

    private boolean isValidEmail(String email) {
        if (email == null) return false;
        return EMAIL_PATTERN.matcher(email).matches();
    }
}