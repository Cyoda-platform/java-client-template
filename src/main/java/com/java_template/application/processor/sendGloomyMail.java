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
 * Processor: sendGloomyMail
 * Responsible for sending mails in the GLOOMY branch.
 */
@Component
public class sendGloomyMail {
    private static final Logger logger = LoggerFactory.getLogger(sendGloomyMail.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final SerializerFactory serializer;

    // Configuration - could be externalized
    private final int maxAttempts = 3;

    public sendGloomyMail(SerializerFactory serializer) {
        this.serializer = serializer;
    }

    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing gloomy mail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid mail payload: mailList required")
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

        if (recipients.isEmpty()) {
            logger.error("No valid recipients after validation, marking as failed");
            return mail;
        }

        boolean allSent = true;
        int attempt = 0;
        while (attempt < maxAttempts) {
            attempt++;
            try {
                boolean sent = sendBatchGloomy(recipients);
                if (sent) {
                    logger.info("Successfully sent gloomy mail to {} recipients on attempt {}", recipients.size(), attempt);
                    allSent = true;
                    break;
                } else {
                    logger.warn("Partial/temporary failure sending gloomy mail on attempt {}", attempt);
                    allSent = false;
                }
            } catch (Exception ex) {
                logger.error("Exception while sending gloomy mail on attempt {}: {}", attempt, ex.getMessage());
                allSent = false;
            }

            try { Thread.sleep(250L * attempt); } catch (InterruptedException ignored) {}
        }

        if (allSent) {
            logger.info("Gloomy mail processing completed successfully at {}", Instant.now());
        } else {
            logger.error("Gloomy mail processing failed after {} attempts", attempt);
        }

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
     * Simulated sending for gloomy mails; replace with real integration.
     */
    private boolean sendBatchGloomy(List<String> recipients) {
        logger.debug("Simulating sending gloomy mail to recipients: {}", recipients);
        return true;
    }
}
