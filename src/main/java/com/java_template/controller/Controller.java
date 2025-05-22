package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Base64Utils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api-cyoda")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    // In-memory caches and tracking
    private final ConcurrentHashMap<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentSkipListSet<String>> factOpenTracking = new ConcurrentHashMap<>();
    private volatile String lastFactId = null;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
        try {
            refreshSubscribersCache();
        } catch (Exception e) {
            logger.warn("Failed to initialize subscribers cache on startup", e);
        }
    }

    // Endpoint to subscribe user
    @PostMapping("/subscribers")
    public ResponseEntity<?> subscribeUser(@RequestBody @Valid EmailRequest emailRequest) throws ExecutionException, InterruptedException {
        String email = emailRequest.getEmail().toLowerCase().trim();
        Subscriber existing = subscribers.get(email);
        if (existing == null) {
            Subscriber newSub = new Subscriber(email, true);
            UUID technicalId = entityService.addItem("subscriber", ENTITY_VERSION, newSub).get();
            logger.info("User subscribed: {} with technicalId {}", email, technicalId);
        } else if (!existing.isActive()) {
            existing.setActive(true);
            UUID technicalId = getTechnicalIdByEmail(email);
            entityService.updateItem("subscriber", ENTITY_VERSION, technicalId, existing).get();
            // After update, update cache to keep in sync
            subscribers.put(email, existing);
            logger.info("User re-subscribed: {} with technicalId {}", email, technicalId);
        } else {
            logger.info("User already subscribed: {}", email);
        }
        return ResponseEntity.ok(Map.of("message", "Subscription successful"));
    }

    // Endpoint to unsubscribe user
    @PostMapping("/subscribers/unsubscribe")
    public ResponseEntity<?> unsubscribeUser(@RequestBody @Valid EmailRequest emailRequest) throws ExecutionException, InterruptedException {
        String email = emailRequest.getEmail().toLowerCase().trim();
        Subscriber sub = subscribers.get(email);
        if (sub == null || !sub.isActive()) {
            logger.info("Unsubscribe attempt for non-existing or inactive email: {}", email);
            return ResponseEntity.ok(Map.of("message", "Email not subscribed"));
        }
        sub.setActive(false);
        UUID technicalId = getTechnicalIdByEmail(email);
        entityService.updateItem("subscriber", ENTITY_VERSION, technicalId, sub).get();
        // Update cache sync
        subscribers.put(email, sub);
        logger.info("User unsubscribed: {}", email);
        return ResponseEntity.ok(Map.of("message", "Unsubscribed successfully"));
    }

    // Endpoint to trigger weekly fact send
    @PostMapping("/facts/send-weekly")
    public ResponseEntity<?> sendWeeklyFact() throws ExecutionException, InterruptedException, IOException {
        logger.info("Triggered weekly cat fact fetch and email send");

        // Fetch cat fact from API - required to provide fact text in entity
        String catFactApiUrl = "https://catfact.ninja/fact";
        String json = restTemplate.getForObject(catFactApiUrl, String.class);
        JsonNode root = objectMapper.readTree(json);
        String fact = root.path("fact").asText(null);
        if (fact == null || fact.isEmpty()) {
            logger.error("Cat fact API returned no fact");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to retrieve cat fact");
        }

        // Create fact entity with new UUID and fact text
        Fact factEntity = new Fact(UUID.randomUUID().toString(), fact);

        // Add fact without workflow
        UUID factTechnicalId = entityService.addItem("fact", ENTITY_VERSION, factEntity).get();

        // Send emails asynchronously here (moved from workflow)
        Set<Map.Entry<String, Subscriber>> entries = subscribers.entrySet();
        for (Map.Entry<String, Subscriber> entry : entries) {
            Subscriber sub = entry.getValue();
            if (sub.isActive()) {
                try {
                    sendEmail(sub.getEmail(), factEntity.getFactId(), fact);
                } catch (Exception e) {
                    logger.error("Failed to send email to {}", sub.getEmail(), e);
                }
            }
        }

        // Initialize tracking
        factOpenTracking.putIfAbsent(factEntity.getFactId(), new ConcurrentSkipListSet<>());
        lastFactId = factEntity.getFactId();

        return ResponseEntity.ok(Map.of("message", "Weekly cat fact sent to subscribers"));
    }

    // Tracking pixel endpoint to record email opens
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
            // 1x1 transparent PNG pixel bytes
            byte[] pixel = new byte[]{
                    (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,
                    0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,
                    0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,
                    0x08,0x06,0x00,0x00,0x00,0x1F,0x15,(byte)0xC4,
                    (byte)0x89,0x00,0x00,0x00,0x0A,0x49,0x44,0x41,
                    0x54,0x78,(byte)0xDA,0x63,0x00,0x01,0x00,0x00,
                    0x05,0x00,0x01,0x0D,0x0A,0x2D,(byte)0xB4,0x00,
                    0x00,0x00,0x00,0x49,0x45,0x4E,0x44,(byte)0xAE,
                    0x42,0x60,(byte)0x82
            };
            response.getOutputStream().write(pixel);
            response.getOutputStream().flush();
        } catch (IOException e) {
            logger.error("Failed to write tracking pixel", e);
        }
    }

    // Report endpoint summarizes subscribers and fact email stats
    @GetMapping("/report/summary")
    public ResponseEntity<?> getReportSummary() throws ExecutionException, InterruptedException {
        // Refresh subscribers cache from entityService to ensure up-to-date
        refreshSubscribersCache();

        int totalSubscribers = subscribers.size();
        long activeSubscribers = subscribers.values().stream().filter(Subscriber::isActive).count();
        int emailsSent = lastFactId == null ? 0 : (int) activeSubscribers;
        int emailOpens = lastFactId == null ? 0 : factOpenTracking.getOrDefault(lastFactId, new ConcurrentSkipListSet<>()).size();

        logger.info("Report requested: total={}, active={}, sent={}, opens={}", totalSubscribers, activeSubscribers, emailsSent, emailOpens);
        return ResponseEntity.ok(Map.of(
                "totalSubscribers", totalSubscribers,
                "activeSubscribers", activeSubscribers,
                "emailsSentThisWeek", emailsSent,
                "emailOpensThisWeek", emailOpens
        ));
    }

    // Helper method to get technicalId of subscriber by email
    private UUID getTechnicalIdByEmail(String email) throws ExecutionException, InterruptedException {
        String condition = String.format("{\"email\":\"%s\"}", email);
        ArrayNode filtered = entityService.getItemsByCondition("subscriber", ENTITY_VERSION, condition).get();
        if (filtered.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Subscriber not found");
        }
        String technicalIdStr = filtered.get(0).path("technicalId").asText(null);
        if (technicalIdStr == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Subscriber technicalId missing");
        }
        return UUID.fromString(technicalIdStr);
    }

    // Helper method to refresh subscribers cache from entityService
    private void refreshSubscribersCache() throws ExecutionException, InterruptedException {
        ArrayNode subsArray = entityService.getItems("subscriber", ENTITY_VERSION).get();
        subscribers.clear();
        for (JsonNode node : subsArray) {
            Subscriber s = objectMapper.convertValue(node, Subscriber.class);
            if (s.getEmail() != null) {
                subscribers.put(s.getEmail(), s);
            }
        }
    }

    // Send email method simulates email sending with logging
    private void sendEmail(String email, String factId, String fact) {
        String emailEncoded = Base64Utils.encodeToUrlSafeString(email.getBytes());
        String trackingPixelUrl = String.format("http://localhost:8080/api-cyoda/facts/track-open/%s/%s", emailEncoded, factId);
        String unsubscribeUrl = "http://localhost:8080/unsubscribe?email=" + email; // TODO: replace with real URL or route

        logger.info("Sending email to {} with fact: {}", email, fact);
        logger.info("Include tracking pixel URL: {}", trackingPixelUrl);
        logger.info("Include unsubscribe URL: {}", unsubscribeUrl);

        // TODO: integrate with actual email service provider
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

    @Data
    @AllArgsConstructor
    static class Fact {
        private String factId;
        private String fact;
    }
}