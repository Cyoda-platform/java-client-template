package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.Base64Utils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentSkipListSet<String>> factOpenTracking = new ConcurrentHashMap<>();
    private volatile String lastFactId = null;

    @PostMapping("/subscribers")
    public ResponseEntity<?> subscribeUser(@RequestBody @Valid EmailRequest emailRequest) {
        String email = emailRequest.getEmail().toLowerCase().trim();
        subscribers.putIfAbsent(email, new Subscriber(email, true));
        logger.info("User subscribed: {}", email);
        return ResponseEntity.ok(Map.of("message", "Subscription successful"));
    }

    @PostMapping("/subscribers/unsubscribe")
    public ResponseEntity<?> unsubscribeUser(@RequestBody @Valid EmailRequest emailRequest) {
        String email = emailRequest.getEmail().toLowerCase().trim();
        Subscriber sub = subscribers.get(email);
        if (sub == null || !sub.isActive()) {
            logger.info("Unsubscribe attempt for non-existing or inactive email: {}", email);
            return ResponseEntity.ok(Map.of("message", "Email not subscribed"));
        }
        sub.setActive(false);
        logger.info("User unsubscribed: {}", email);
        return ResponseEntity.ok(Map.of("message", "Unsubscribed successfully"));
    }

    @PostMapping("/facts/send-weekly")
    public ResponseEntity<?> sendWeeklyFact() {
        logger.info("Triggered weekly cat fact fetch and email send");
        String catFactApiUrl = "https://catfact.ninja/fact";
        try {
            String json = restTemplate.getForObject(catFactApiUrl, String.class);
            JsonNode root = objectMapper.readTree(json);
            String fact = root.path("fact").asText(null);
            if (fact == null || fact.isEmpty()) {
                logger.error("Cat fact API returned no fact");
                throw new ResponseStatusException(BAD_GATEWAY, "Failed to retrieve cat fact");
            }
            String factId = UUID.randomUUID().toString();
            lastFactId = factId;
            logger.info("Fetched cat fact: {}", fact);
            CompletableFuture.runAsync(() -> sendEmailsToAllSubscribers(factId, fact)); // fire-and-forget
            return ResponseEntity.ok(Map.of("message", "Weekly cat fact sent to subscribers"));
        } catch (IOException e) {
            logger.error("Error parsing cat fact API response", e);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to parse cat fact");
        } catch (Exception e) {
            logger.error("Error fetching cat fact or sending emails", e);
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to send weekly cat fact");
        }
    }

    @GetMapping(value = "/facts/track-open/{emailEncoded}/{factId}", produces = MediaType.IMAGE_PNG_VALUE)
    public void trackEmailOpen(@PathVariable String emailEncoded, @PathVariable String factId, HttpServletResponse response) {
        try {
            String email = new String(Base64Utils.decodeFromUrlSafeString(emailEncoded));
            factOpenTracking.computeIfAbsent(factId, k -> new ConcurrentSkipListSet<>()).add(email);
            logger.info("Tracked open for factId {} and email {}", factId, email);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid base64 email encoding in tracking pixel request");
        }
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        try {
            byte[] pixel = new byte[]{(byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,0x08,0x06,0x00,0x00,0x00,0x1F,0x15,(byte)0xC4,(byte)0x89,0x00,0x00,0x00,0x0A,0x49,0x44,0x41,0x54,0x78,(byte)0xDA,0x63,0x00,0x01,0x00,0x00,0x05,0x00,0x01,0x0D,0x0A,0x2D,(byte)0xB4,0x00,0x00,0x00,0x00,0x49,0x45,0x4E,0x44,(byte)0xAE,0x42,0x60,(byte)0x82};
            response.getOutputStream().write(pixel);
            response.getOutputStream().flush();
        } catch (IOException e) {
            logger.error("Failed to write tracking pixel", e);
        }
    }

    @GetMapping("/report/summary")
    public ResponseEntity<?> getReportSummary() {
        int totalSubscribers = subscribers.size();
        long activeSubscribers = subscribers.values().stream().filter(Subscriber::isActive).count();
        int emailsSent = lastFactId == null ? 0 : (int) subscribers.values().stream().filter(Subscriber::isActive).count();
        int emailOpens = lastFactId == null ? 0 : factOpenTracking.getOrDefault(lastFactId, new ConcurrentSkipListSet<>()).size();
        logger.info("Report requested: total={}, active={}, sent={}, opens={}", totalSubscribers, activeSubscribers, emailsSent, emailOpens);
        return ResponseEntity.ok(Map.of(
                "totalSubscribers", totalSubscribers,
                "activeSubscribers", activeSubscribers,
                "emailsSentThisWeek", emailsSent,
                "emailOpensThisWeek", emailOpens
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("API error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class EmailRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @AllArgsConstructor
    static class Subscriber {
        private String email;
        private boolean active;
    }

    private void sendEmailsToAllSubscribers(String factId, String fact) {
        Set<Map.Entry<String, Subscriber>> entries = subscribers.entrySet();
        for (Map.Entry<String, Subscriber> entry : entries) {
            Subscriber sub = entry.getValue();
            if (sub.isActive()) {
                try {
                    sendEmail(sub.getEmail(), factId, fact);
                } catch (Exception e) {
                    logger.error("Failed to send email to {}", sub.getEmail(), e);
                }
            }
        }
    }

    private void sendEmail(String email, String factId, String fact) {
        String emailEncoded = Base64Utils.encodeToUrlSafeString(email.getBytes());
        String trackingPixelUrl = String.format("http://localhost:8080/api/facts/track-open/%s/%s", emailEncoded, factId);
        String unsubscribeUrl = "http://localhost:8080/unsubscribe?email=" + email; // TODO: replace with real URL
        logger.info("Sending email to {} with fact: {}", email, fact);
        logger.info("Include tracking pixel URL: {}", trackingPixelUrl);
        logger.info("Include unsubscribe URL: {}", unsubscribeUrl);
        // TODO: integrate with real email service provider
    }
}