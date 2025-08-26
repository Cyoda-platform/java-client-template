package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class ValidateUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Basic email and phone patterns used for additional validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9+()\\-\\s]{6,30}$");

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
            logger.warn("User entity is null in processing context");
            return null;
        }

        List<String> validationErrors = new ArrayList<>();

        // Basic checks already covered by User.isValid(), add stricter format validations here.

        // Email format
        String email = entity.getEmail();
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            validationErrors.add("Invalid email format");
        }

        // Username length and characters
        String username = entity.getUsername();
        if (username == null || username.isBlank() || username.length() < 3) {
            validationErrors.add("Username must be at least 3 characters");
        }

        // Name presence (isValid covers not blank) - additional trimming check
        String name = entity.getName();
        if (name == null || name.isBlank() || name.trim().length() == 0) {
            validationErrors.add("Name must be provided");
        }

        // Phone number if provided should match simple pattern
        String phone = entity.getPhone();
        if (phone != null && !phone.isBlank()) {
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                validationErrors.add("Phone number format is invalid");
            }
        }

        // Address fields: if address provided, ensure city and street already checked by isValid(),
        // but ensure zipcode if present is not blank
        User.Address addr = entity.getAddress();
        if (addr != null) {
            String zipcode = addr.getZipcode();
            if (zipcode != null && zipcode.isBlank()) {
                validationErrors.add("Address.zipcode, if provided, must not be blank");
            }
        }

        // Decide processing status based on validation result
        if (validationErrors.isEmpty()) {
            // Validation passed -> move to TRANSFORMED stage per workflow
            entity.setProcessingStatus("TRANSFORMED");
            logger.info("User id={} validated successfully. Marking as TRANSFORMED.", entity.getId());
        } else {
            // Validation failed -> mark as FAILED and log reasons
            entity.setProcessingStatus("FAILED");
            logger.warn("User id={} validation failed: {}", entity.getId(), validationErrors);
            // Optionally, we could attach a serialized representation of errors to storedReference or rawPayload,
            // but per rules we should only modify this entity's state; leave rawPayload as-is.
        }

        return entity;
    }
}