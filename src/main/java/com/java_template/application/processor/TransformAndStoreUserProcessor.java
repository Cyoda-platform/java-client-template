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

import java.time.Instant;

@Component
public class TransformAndStoreUserProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(TransformAndStoreUserProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public TransformAndStoreUserProcessor(SerializerFactory serializerFactory) {
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

        if (entity == null) return null;

        // Only transform and store users that have been marked VALID by previous processors
        String status = entity.getValidationStatus();
        if (status != null && "VALID".equalsIgnoreCase(status.trim())) {

            // Normalize full name to Title Case (simple implementation)
            String fullName = entity.getFullName();
            if (fullName != null && !fullName.isBlank()) {
                entity.setFullName(titleCase(fullName));
            }

            // Normalize phone: remove non-digit characters and prefix with '+' if missing
            String phone = entity.getPhone();
            if (phone != null && !phone.isBlank()) {
                String digitsOnly = phone.replaceAll("\\D+", "");
                if (!digitsOnly.isBlank()) {
                    if (!digitsOnly.startsWith("+")) {
                        // Prefix with '+' to indicate international format; keep digits as-is
                        entity.setPhone("+" + digitsOnly);
                    } else {
                        entity.setPhone(digitsOnly);
                    }
                } else {
                    // if phone becomes empty after cleaning, clear it
                    entity.setPhone(null);
                }
            }

            // Mark transformation time
            entity.setTransformedAt(Instant.now());

            // Simulate successful storage by setting storedAt timestamp.
            // Actual persistence of this entity is handled by Cyoda workflow and should not be done via entityService for this entity.
            entity.setStoredAt(Instant.now());

            logger.info("Transformed and marked user id={} as stored (transformedAt={}, storedAt={})",
                entity.getId(), entity.getTransformedAt(), entity.getStoredAt());
        } else {
            logger.info("Skipping transform/store for user id={} due to validationStatus={}", entity.getId(), status);
        }

        return entity;
    }

    // Helper: simple title case implementation
    private String titleCase(String input) {
        if (input == null || input.isBlank()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        boolean space = true;
        for (char c : input.toLowerCase().toCharArray()) {
            if (Character.isWhitespace(c)) {
                space = true;
                sb.append(c);
            } else {
                if (space) {
                    sb.append(Character.toUpperCase(c));
                    space = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}