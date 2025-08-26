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
import java.util.Locale;

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
            logger.warn("Received null User entity in processing context");
            return null;
        }

        logger.info("Transforming user id={}, username={}", entity.getId(), entity.getUsername());

        // Normalize full name: trim and title case each word
        String fullName = entity.getFullName();
        if (fullName != null) {
            fullName = fullName.trim();
            entity.setFullName(titleCase(fullName));
        }

        // Normalize username to lower-case and trim
        String username = entity.getUsername();
        if (username != null) {
            entity.setUsername(username.trim().toLowerCase(Locale.ROOT));
        }

        // Normalize email: trim and lower-case
        String email = entity.getEmail();
        if (email != null) {
            entity.setEmail(email.trim().toLowerCase(Locale.ROOT));
        }

        // Normalize phone: remove non-digits, add '+' if possible
        String phone = entity.getPhone();
        if (phone != null) {
            String digits = phone.replaceAll("\\D+", "");
            if (!digits.isEmpty()) {
                // Simple normalization: if digits length > 0 and does not start with country code, keep as-is with +
                if (digits.startsWith("0")) {
                    // remove leading zero and prefix +
                    digits = digits.replaceFirst("^0+", "");
                    entity.setPhone("+" + digits);
                } else if (digits.length() >= 10 && !digits.startsWith("+")) {
                    entity.setPhone("+" + digits);
                } else {
                    entity.setPhone(digits);
                }
            } else {
                entity.setPhone(null);
            }
        }

        // Set transformed timestamp
        Instant now = Instant.now();
        entity.setTransformedAt(now);

        // Since this processor is TransformAndStore, mark storedAt as now as well to indicate persistence step completed.
        // Note: actual persistence of this entity will be handled by Cyoda; we're only updating its state.
        entity.setStoredAt(now);

        // If validationStatus is RAW, keep it as-is (assumes validation happened earlier).
        // If not set, default to VALID after transformation.
        if (entity.getValidationStatus() == null || entity.getValidationStatus().isBlank()) {
            entity.setValidationStatus("VALID");
        }

        logger.info("Transformed user id={}, username={}, transformedAt={}", entity.getId(), entity.getUsername(), entity.getTransformedAt());

        return entity;
    }

    private String titleCase(String input) {
        if (input.isBlank()) return input;
        StringBuilder sb = new StringBuilder(input.length());
        boolean space = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                space = true;
                sb.append(c);
            } else {
                if (space) {
                    sb.append(Character.toTitleCase(c));
                } else {
                    sb.append(Character.toLowerCase(c));
                }
                space = false;
            }
        }
        return sb.toString();
    }
}