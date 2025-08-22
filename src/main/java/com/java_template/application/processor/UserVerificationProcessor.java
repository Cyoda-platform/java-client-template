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

import java.util.regex.Pattern;

@Component
public class UserVerificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UserVerificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");

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
        User user = context.entity();

        try {
            // Basic automatic verification checks:
            // - Email must have local and domain parts and domain contains a dot.
            // - Contact, if present, must contain at least 7 digits.
            // - Fallback: name must be present and reasonably long to help auto-verify.
            String email = user.getEmail() != null ? user.getEmail().trim() : null;
            String contact = user.getContact() != null ? user.getContact().trim() : null;
            String name = user.getName() != null ? user.getName().trim() : null;

            boolean emailOk = false;
            if (email != null && !email.isBlank() && email.contains("@")) {
                String[] parts = email.split("@", 2);
                if (parts.length == 2) {
                    String domain = parts[1];
                    String local = parts[0];
                    if (!local.isBlank() && domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".")) {
                        emailOk = true;
                    }
                }
            }

            boolean contactOk = false;
            if (contact != null && !contact.isBlank()) {
                int digits = 0;
                var m = DIGIT_PATTERN.matcher(contact);
                while (m.find()) digits++;
                contactOk = digits >= 7;
            }

            boolean nameOk = name != null && name.length() >= 2;

            // Decide verification:
            // - If email is good and either contact is good or name looks valid -> auto-verify
            // - Otherwise mark as not verified and record a note indicating pending/failed verification
            if (emailOk && (contactOk || nameOk)) {
                user.setVerified(Boolean.TRUE);
                appendNote(user, "auto-verified: basic checks passed");
                logger.info("User {} auto-verified by UserVerificationProcessor", safeId(user));
            } else {
                user.setVerified(Boolean.FALSE);
                String reason;
                if (!emailOk) reason = "invalid email";
                else if (!contactOk) reason = "insufficient contact info";
                else reason = "verification checks failed";
                appendNote(user, "verification_pending: " + reason);
                logger.info("User {} left unverified ({})", safeId(user), reason);
            }
        } catch (Exception e) {
            // On unexpected errors, do not throw; mark as not verified and record the error in notes.
            try {
                user.setVerified(Boolean.FALSE);
                appendNote(user, "verification_error: " + e.getMessage());
            } catch (Exception ex) {
                logger.error("Failed to set verification state for user {}: {}", safeId(user), ex.getMessage());
            }
            logger.error("Error during user verification for {}: {}", safeId(user), e.getMessage(), e);
        }

        return user;
    }

    private void appendNote(User user, String note) {
        String existing = user.getNotes();
        if (existing == null || existing.isBlank()) {
            user.setNotes(note);
        } else {
            user.setNotes(existing + "\n" + note);
        }
    }

    private String safeId(User user) {
        try {
            return user != null && user.getId() != null ? user.getId() : "<unknown>";
        } catch (Exception e) {
            return "<unknown>";
        }
    }
}