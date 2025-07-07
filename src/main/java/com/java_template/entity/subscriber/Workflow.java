package com.java_template.entity.subscriber;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

import static com.java_template.common.config.Config.*;

@Component("subscriber")
@RequiredArgsConstructor
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    private boolean emailValid;
    private boolean duplicateEmail;

    // Condition function: validate email format
    public ObjectNode isEmailValid(ObjectNode entity) {
        String emailRaw = entity.path("email").asText(null);
        boolean valid = emailRaw != null && !emailRaw.isBlank() && emailRaw.matches("^[\\w-.]+@[\\w-]+\\.[\\w-.]+$");
        logger.info("Email validation result for '{}': {}", emailRaw, valid);
        entity.put("emailValid", valid);
        emailValid = valid;
        return entity;
    }

    // Action function: normalize email to lowercase
    public ObjectNode normalizeEmail(ObjectNode entity) {
        String emailRaw = entity.path("email").asText(null);
        if (emailRaw != null) {
            String emailNorm = emailRaw.toLowerCase(Locale.ROOT);
            entity.put("email", emailNorm);
            logger.info("Normalized email from '{}' to '{}'", emailRaw, emailNorm);
        }
        return entity;
    }

    // Condition function: check if email is duplicate (mocked using entity attribute, to be replaced with real check)
    public ObjectNode isDuplicateEmail(ObjectNode entity) {
        // TODO: Replace with actual async duplicate check via entityService or DB
        // Here we simulate by checking a boolean flag set externally or default false
        boolean isDuplicate = entity.path("isDuplicate").asBoolean(false);
        logger.info("Duplicate email check: {}", isDuplicate);
        duplicateEmail = isDuplicate;
        entity.put("isDuplicateEmail", isDuplicate);
        return entity;
    }

    // Action function: set subscribedAt if missing or invalid
    public ObjectNode setSubscribedAtIfMissing(ObjectNode entity) {
        if (!entity.hasNonNull("subscribedAt")) {
            entity.put("subscribedAt", Instant.now().toString());
            logger.info("subscribedAt was missing, set to now");
        } else {
            try {
                Instant.parse(entity.get("subscribedAt").asText());
                logger.info("subscribedAt is valid");
            } catch (Exception e) {
                entity.put("subscribedAt", Instant.now().toString());
                logger.warn("subscribedAt invalid, reset to now");
            }
        }
        return entity;
    }

    // Transition condition helper for negation (for workflow engine to use)
    public boolean isEmailValidCondition(ObjectNode entity) {
        return emailValid;
    }

    public boolean isEmailInvalidCondition(ObjectNode entity) {
        return !emailValid;
    }

    public boolean isDuplicateEmailCondition(ObjectNode entity) {
        return duplicateEmail;
    }

    public boolean isNotDuplicateEmailCondition(ObjectNode entity) {
        return !duplicateEmail;
    }
}