package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyodaEntity")
@Validated
@AllArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "subscriber";
    private static final String SENT_FACT_ENTITY_NAME = "sentFact";

    // In-memory store for interactions
    private final Map<UUID, List<Interaction>> interactions = Collections.synchronizedMap(new HashMap<>());

    // DTOs
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignUpRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionRequest {
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$")
        private String subscriberId;

        @NotBlank
        @Pattern(regexp = "open|click")
        private String interactionType;

        @NotBlank
        private String timestamp;
    }

    // Entities
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        private UUID subscriberId;
        private String email;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Interaction {
        private UUID subscriberId;
        private String interactionType;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentFact {
        private String fact;
        private Instant timestamp;
        private int sentCount;
    }

    // Workflow function to process subscriber entity before persisting
    private CompletableFuture<ObjectNode> processSubscriber(ObjectNode subscriberNode) {
        // Ensure status field
        if (!subscriberNode.hasNonNull("status")) {
            subscriberNode.put("status", "pending");
        }

        // Async side effect: send welcome email
        return CompletableFuture.runAsync(() -> {
            String email = subscriberNode.path("email").asText(null);
            if (email != null) {
                logger.info("Async sending welcome email to: {}", email);
                // Real email sending logic should be here
            }
        }).thenApply(v -> subscriberNode);
    }

    // Workflow function to process sentFact entity before persisting
    private CompletableFuture<ObjectNode> processSentFact(ObjectNode sentFactNode) {
        String fact = sentFactNode.path("fact").asText(null);
        int sentCount = sentFactNode.path("sentCount").asInt(0);

        return CompletableFuture.runAsync(() -> {
            logger.info("Async sending fact to {} subscribers: {}", sentCount, fact);
            // Real email/send logic should be here
        }).thenApply(v -> sentFactNode);
    }

    // Endpoint for subscriber sign up
    @PostMapping("/subscribers")
    public Subscriber signUp(@RequestBody @Valid SignUpRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received signup request for email: {}", request.getEmail());

        // Check for existing email
        ArrayNode allSubs = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
        for (JsonNode node : allSubs) {
            if (request.getEmail().equalsIgnoreCase(node.path("email").asText())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
            }
        }

        // Create new subscriber as ObjectNode
        ObjectNode newSubscriberNode = objectMapper.createObjectNode();
        UUID subscriberId = UUID.randomUUID();
        newSubscriberNode.put("subscriberId", subscriberId.toString());
        newSubscriberNode.put("email", request.getEmail());

        // Persist with workflow
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                newSubscriberNode,
                this::processSubscriber
        );

        UUID technicalId = idFuture.get();

        // Prepare response
        String status = newSubscriberNode.path("status").asText("pending");
        return new Subscriber(subscriberId, request.getEmail(), status);
    }

    // Endpoint to send weekly cat fact
    @PostMapping("/facts/sendWeekly")
    public Map<String, Object> sendWeeklyFact() throws Exception {
        logger.info("Triggering weekly fact send");

        String factText;

        try {
            String resp = restTemplate.getForObject(new URI("https://catfact.ninja/fact"), String.class);
            JsonNode root = objectMapper.readTree(resp);
            factText = root.path("fact").asText();
            if(factText == null || factText.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid fact received");
            }
        } catch (Exception e) {
            logger.error("Failed to fetch cat fact", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch cat fact");
        }

        int count;
        try {
            ArrayNode allSubs = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
            count = allSubs.size();
        } catch (Exception e) {
            logger.error("Failed to fetch subscribers", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch subscribers");
        }

        ObjectNode sentFactNode = objectMapper.createObjectNode();
        sentFactNode.put("fact", factText);
        sentFactNode.put("timestamp", Instant.now().toString());
        sentFactNode.put("sentCount", count);

        // Persist sentFact with workflow for async send
        entityService.addItem(
                SENT_FACT_ENTITY_NAME,
                ENTITY_VERSION,
                sentFactNode,
                this::processSentFact
        );

        Map<String, Object> out = new HashMap<>();
        out.put("sentCount", count);
        out.put("catFact", factText);
        out.put("timestamp", Instant.now().toString());
        return out;
    }

    // Get all subscribers
    @GetMapping("/subscribers")
    public List<Subscriber> getSubscribers() throws ExecutionException, InterruptedException {
        ArrayNode allSubs = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
        List<Subscriber> list = new ArrayList<>();
        for (JsonNode node : allSubs) {
            Subscriber sub = objectMapper.convertValue(node, Subscriber.class);
            list.add(sub);
        }
        return list;
    }

    // Get report summary
    @GetMapping("/reports/summary")
    public Map<String, Object> getReportSummary() throws ExecutionException, InterruptedException {
        int totalSubs;
        try {
            ArrayNode allSubs = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
            totalSubs = allSubs.size();
        } catch (Exception e) {
            totalSubs = 0;
        }

        int totalSent;
        try {
            ArrayNode sentFacts = entityService.getItems(SENT_FACT_ENTITY_NAME, ENTITY_VERSION).get();
            totalSent = sentFacts.size();
        } catch (Exception e) {
            totalSent = 0;
        }

        long opens = interactions.values().stream().flatMap(List::stream)
                .filter(i -> "open".equals(i.getInteractionType())).count();
        long clicks = interactions.values().stream().flatMap(List::stream)
                .filter(i -> "click".equals(i.getInteractionType())).count();

        Instant last = null;
        try {
            ArrayNode sentFactsArray = entityService.getItems(SENT_FACT_ENTITY_NAME, ENTITY_VERSION).get();
            for (JsonNode factNode : sentFactsArray) {
                String tsStr = factNode.path("timestamp").asText(null);
                if (tsStr != null) {
                    Instant ts = Instant.parse(tsStr);
                    if (last == null || ts.isAfter(last)) {
                        last = ts;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalSubscribers", totalSubs);
        summary.put("totalFactsSent", totalSent);
        summary.put("totalEmailOpens", opens);
        summary.put("totalEmailClicks", clicks);
        summary.put("lastFactSentAt", last != null ? last.toString() : null);
        return summary;
    }

    // Track interaction
    @PostMapping("/interactions")
    public Map<String, Object> trackInteraction(@RequestBody @Valid InteractionRequest request) throws ExecutionException, InterruptedException {
        UUID sid;
        try {
            sid = UUID.fromString(request.getSubscriberId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID");
        }

        ObjectNode subscriberNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, sid).get();
        if (subscriberNode == null || subscriberNode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
        }

        Instant ts;
        try {
            ts = Instant.parse(request.getTimestamp());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timestamp");
        }

        Interaction inter = new Interaction(sid, request.getInteractionType(), ts);
        interactions.computeIfAbsent(sid, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(inter);

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "recorded");
        resp.put("subscriberId", sid);
        resp.put("interactionType", request.getInteractionType());
        resp.put("timestamp", ts.toString());
        return resp;
    }

    // Exception handlers
    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleStatus(ResponseStatusException ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", ex.getStatusCode().value());
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return err;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleAll(Exception ex) {
        Map<String, Object> err = new HashMap<>();
        err.put("status", 500);
        err.put("error", "Internal Server Error");
        err.put("message", "Unexpected error");
        logger.error("Unexpected error", ex);
        return err;
    }
}