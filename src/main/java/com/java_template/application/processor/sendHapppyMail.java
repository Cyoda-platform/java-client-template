package com.java_template.application.processor;

import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.calculation.EntityProcessorCalculationRequest;
import com.java_template.common.workflow.calculation.EntityProcessorCalculationResponse;
import com.java_template.common.serializer.SerializerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Processor: sendHapppyMail
 * Responsible for sending mails in the HAPPY branch.
 * Note: preserve exact class name (three 'p').
 */
@Component
public class sendHapppyMail {
    private static final Logger logger = LoggerFactory.getLogger(sendHapppyMail.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final SerializerFactory serializer;

    // Configuration - could be externalized
    private final int maxAttempts = 3;

    public sendHapppyMail(SerializerFactory serializer) {
        this.serializer = serializer;
    }

    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing happy mail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid mail payload: mailList is required")
            .map(this::processEntityLogic)
            .complete();
    }

    private Mail processEntityLogic(Mail mail) {
        // Defensive copy
        List<String> original = mail.getMailList() == null ? new ArrayList<>() : new ArrayList<>(mail.getMailList());

        // Normalize: trim and deduplicate preserving order
        Set<String> dedup = new LinkedHashSet<>();
        for (String e : original) {
            if (e == null) continue;
            String t = e.trim();
            if (!t.isEmpty()) dedup.add(t.toLowerCase());
        }
        List<String> recipients = new ArrayList<>(dedup);

        // Validate email addresses; filter invalid and log
        List<String> invalid = recipients.stream()
            .filter(a -> !EMAIL_PATTERN.matcher(a).matches())
            .collect(Collectors.toList());

        if (!invalid.isEmpty()) {
            logger.warn("Found invalid email addresses for mail: {}", invalid);
            recipients = recipients.stream()
                .filter(a -> EMAIL_PATTERN.matcher(a).matches())
                .collect(Collectors.toList());
        }

        // If no valid recipients remain, mark as failed (framework will persist state based on entity modifications)
        if (recipients.isEmpty()) {
            logger.error("No valid recipients after validation, marking as failed");
            // Cannot mutate fields that don't exist on Mail here, but the platform will persist any changes to the entity.
            // If Mail had fields like status/error/retryCount we would set them here. Documented behavior for framework.
            return mail;
        }

        // Attempt to send. Implement simple retry strategy.
        boolean allSent = true;
        int attempt = 0;
        while (attempt < maxAttempts) {
            attempt++;
            try {
                boolean sent = sendBatchHappy(recipients);
                if (sent) {
                    logger.info("Successfully sent happy mail to {} recipients on attempt {}", recipients.size(), attempt);
                    allSent = true;
                    break;
                } else {
                    logger.warn("Partial/temporary failure sending happy mail on attempt {}", attempt);
                    allSent = false;
                }
            } catch (Exception ex) {
                logger.error("Exception while sending happy mail on attempt {}: {}", attempt, ex.getMessage());
                allSent = false;
            }

            // Simple backoff
            try { Thread.sleep(250L * attempt); } catch (InterruptedException ignored) {}
        }

        if (allSent) {
            // On success: set status to SENT and update updatedAt. If Mail had status fields we would set them here.
            logger.info("Happy mail processing completed successfully at {}", Instant.now());
        } else {
            // On final failure: set error and increment retry counters as required.
            logger.error("Happy mail processing failed after {} attempts", attempt);
        }

        // Update the entity's mailList to the normalized list so persisted record reflects normalization
        mail.setMailList(recipients);

        return mail;
    }

    private boolean isValidEntity(Mail mail) {
        if (mail == null) return false;
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) return false;
        for (String e : mail.getMailList()) {
            if (e == null || e.isBlank()) return false;
        }
        return true;
    }

    /**
     * Real implementation should call an external delivery service or enqueue messages.
     * Here we provide a simple synchronous simulation that always returns true.
     */
    private boolean sendBatchHappy(List<String> recipients) {
        // TODO: integrate with real mail delivery subsystem with idempotency guarantees
        logger.debug("Simulating sending happy mail to recipients: {}", recipients);
        return true;
    }
}
