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

import java.time.Instant;
import java.util.regex.Pattern;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9\\-\\s]{7,20}$");

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

        // Normalize basic fields
        if (entity.getName() != null) {
            entity.setName(entity.getName().trim());
        }
        if (entity.getEmail() != null) {
            entity.setEmail(entity.getEmail().trim().toLowerCase());
        }
        if (entity.getPhone() != null) {
            entity.setPhone(entity.getPhone().trim());
        }

        // Validate email format (stricter than isValid's basic check)
        String email = entity.getEmail();
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            logger.warn("User validation failed - invalid email: {}", email);
            throw new IllegalArgumentException("Invalid email format");
        }

        // Validate phone format if provided (phone is optional)
        String phone = entity.getPhone();
        if (phone != null && !phone.isBlank()) {
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                logger.warn("User validation failed - invalid phone: {}", phone);
                throw new IllegalArgumentException("Invalid phone format");
            }
        }

        // Ensure timestamps: set createdAt if missing, always update updatedAt
        String now = Instant.now().toString();
        if (entity.getCreatedAt() == null || entity.getCreatedAt().isBlank()) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        // No add/update/delete operations on this entity here; Cyoda will persist changes automatically.
        return entity;
    }
}