package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.serializer.SerializerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

/**
 * Processor: sendGloomyMail
 * Responsible for sending mails in the GLOOMY branch.
 */
@Component
public class sendGloomyMail {
    private static final Logger logger = LoggerFactory.getLogger(sendGloomyMail.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final SerializerFactory serializer;

    private final int maxAttempts = Integer.parseInt(System.getenv().getOrDefault("MAIL_MAX_ATTEMPTS", "3"));
    private final long baseBackoffMs = Long.parseLong(System.getenv().getOrDefault("MAIL_BASE_BACKOFF_MS", "250"));
    private final String deliveryUrl = System.getenv().getOrDefault("MAIL_DELIVERY_URL", "");
    private final String deliveryApiKey = System.getenv().getOrDefault("MAIL_DELIVERY_API_KEY", "");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        if (mail.getCreatedAt() == null) {
            mail.setCreatedAt(Instant.now().toString());
        }
        if (mail.getRetryCount() == null) {
            mail.setRetryCount(0);
        }

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
            logger.error("No valid recipients after validation, marking as FAILED");
            mail.setStatus("FAILED");
            mail.setError("No valid recipients after validation");
            mail.setUpdatedAt(Instant.now().toString());
            return mail;
        }

        mail.setStatus("SENDING_GLOOMY");
        mail.setUpdatedAt(Instant.now().toString());

        boolean allSent = false;
        String lastError = null;

        int attempt = mail.getRetryCount();
        while (attempt < maxAttempts) {
            attempt++;
            mail.setRetryCount(attempt);
            mail.setUpdatedAt(Instant.now().toString());

            try {
                boolean sent = sendBatchGloomy(recipients, mail);
                if (sent) {
                    logger.info("Successfully sent gloomy mail to {} recipients on attempt {}", recipients.size(), attempt);
                    allSent = true;
                    lastError = null;
                    break;
                } else {
                    lastError = "Delivery subsystem returned failure";
                    logger.warn("Partial/temporary failure sending gloomy mail on attempt {}", attempt);
                }
            } catch (Exception ex) {
                lastError = ex.getMessage();
                logger.error("Exception while sending gloomy mail on attempt {}: {}", attempt, ex.getMessage());
            }

            try { Thread.sleep(baseBackoffMs * attempt); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }

        if (allSent) {
            mail.setStatus("SENT");
            mail.setError(null);
            mail.setUpdatedAt(Instant.now().toString());
            logger.info("Gloomy mail processing completed successfully at {}", Instant.now());
        } else {
            mail.setStatus("FAILED");
            mail.setError(lastError == null ? "Unknown error" : lastError);
            mail.setUpdatedAt(Instant.now().toString());
            logger.error("Gloomy mail processing failed after {} attempts, error={}", attempt, mail.getError());
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

    private boolean sendBatchGloomy(List<String> recipients, Mail mail) throws IOException, InterruptedException {
        if (deliveryUrl == null || deliveryUrl.isBlank()) {
            logger.debug("No MAIL_DELIVERY_URL configured, simulating delivery for gloomy mail");
            return true;
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("branch", "GLOOMY");
        payload.put("technicalId", mail.getCreatedAt() == null ? UUID.randomUUID().toString() : mail.getCreatedAt());
        ArrayNode arr = payload.putArray("recipients");
        for (String r : recipients) arr.add(r);

        String body = objectMapper.writeValueAsString(payload);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(deliveryUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));

        String idempotency = mail.getCreatedAt() == null ? UUID.randomUUID().toString() : mail.getCreatedAt();
        builder.header("Idempotency-Key", idempotency);

        if (deliveryApiKey != null && !deliveryApiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + deliveryApiKey);
        }

        HttpRequest req = builder.build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        int code = resp.statusCode();
        logger.debug("Delivery response code={}, body={}", code, resp.body());
        return code >= 200 && code < 300;
    }
}
