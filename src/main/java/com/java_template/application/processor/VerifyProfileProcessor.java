package com.java_template.application.processor;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Component
public class VerifyProfileProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VerifyProfileProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public VerifyProfileProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(User.class)
            .withErrorHandler((error, entity) -> {
                    logger.error("Failed to extract entity: {}", error.getMessage(), error);
                    return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
                })
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
        if (user == null) return null;

        // Business logic: Verify profile using IdentityVerifiedCriterion
        // Criteria:
        //  - fullName must be present (already enforced by isValid)
        //  - email must be syntactically valid (basic checks)
        //  - registeredAt must be present (already enforced by isValid)
        //  - phone if present should have at least 7 digits (loose check)
        boolean emailValid = false;
        String email = user.getEmail();
        if (email != null) {
            String trimmed = email.trim();
            int at = trimmed.indexOf('@');
            if (at > 0 && at < trimmed.length() - 1) {
                String domain = trimmed.substring(at + 1);
                if (domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".")) {
                    emailValid = true;
                }
            }
        }

        boolean phoneValid = true; // optional: valid if absent
        String phone = user.getPhone();
        if (phone != null && !phone.isBlank()) {
            int digits = 0;
            for (char c : phone.toCharArray()) {
                if (Character.isDigit(c)) digits++;
            }
            phoneValid = digits >= 7;
        }

        boolean identityVerified = emailValid && phoneValid;

        // Attach verification result into preferences map for downstream processors/records
        Map<String, Object> prefs = user.getPreferences();
        if (prefs == null) {
            prefs = new HashMap<>();
            user.setPreferences(prefs);
        }
        prefs.put("identityVerified", identityVerified);
        prefs.put("emailValid", emailValid);
        prefs.put("phoneValid", phoneValid);

        // Set status based on verification outcome.
        // If identity verified, advance to PROFILE_VERIFIED; otherwise keep as REGISTERED
        String currentStatus = user.getStatus();
        if (identityVerified) {
            user.setStatus("PROFILE_VERIFIED");
            logger.info("User {} marked as PROFILE_VERIFIED", user.getUserId());
        } else {
            // Do not advance workflow; keep as REGISTERED or set explicit needs verification marker
            // If current status is null or not PROFILE_VERIFIED, ensure it remains REGISTERED
            if (currentStatus == null || !"PROFILE_VERIFIED".equalsIgnoreCase(currentStatus)) {
                user.setStatus("REGISTERED");
            }
            // add reason detail in preferences
            String reason = "";
            if (!emailValid) reason += "email_invalid;";
            if (!phoneValid) reason += "phone_invalid;";
            if (!reason.isBlank()) prefs.put("profileVerificationReason", reason);
            logger.info("User {} verification failed: {}", user.getUserId(), reason);
        }

        return user;
    }
}