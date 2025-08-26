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

    // Simple email pattern (sufficient for basic validation)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

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
        User user = context.entity();

        if (user == null) {
            logger.warn("User entity is null in processing context");
            return null;
        }

        // Basic required field checks
        boolean hasUsername = user.getUsername() != null && !user.getUsername().isBlank();
        boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();

        // Email format validation
        boolean emailFormatValid = hasEmail && EMAIL_PATTERN.matcher(user.getEmail().trim()).matches();

        if (!hasUsername || !hasEmail) {
            user.setValidationStatus("INVALID");
            logger.info("User marked INVALID due to missing required fields (username/email). usernamePresent={}, emailPresent={}",
                hasUsername, hasEmail);
            return user;
        }

        if (!emailFormatValid) {
            user.setValidationStatus("INVALID");
            logger.info("User marked INVALID due to invalid email format: {}", user.getEmail());
            return user;
        }

        // If all basic checks pass, mark as VALID.
        user.setValidationStatus("VALID");
        logger.info("User marked VALID: username={}, email={}", user.getUsername(), user.getEmail());

        // Note: Duplicate detection and further checks may be implemented in future iterations
        // when EntityService search capabilities are available here without risking unknown API usage.

        return user;
    }
}