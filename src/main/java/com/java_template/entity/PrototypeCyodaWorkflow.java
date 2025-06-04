```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.function.Function;

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

    private static final String ENTITY_NAME = "subscriber"; // entityModel name

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriber {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private UUID subscriberId;
        private String email;
        private String status;
    }

    @Data
    public static class SignUpRequest {
        @NotBlank
        @Email
        private String email;
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
    public static class InteractionRequest {
        @NotBlank
        @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$")
        private String subscriberId;

        @NotBlank
        @Pattern(regexp = "open|click")
        private String interactionType;

        @NotBlank
        private String timestamp; // ISO-8601 string
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentFact {
        private String fact;
        private Instant timestamp;
        private int sentCount;
    }

    private final List<SentFact> sentFacts = Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, List<Interaction>> interactions = Collections.synchronizedMap(new HashMap<>());

    // Workflow function to process Subscriber entity before persisting
    private Subscriber processSubscriber(Subscriber subscriber) {
        // Example: You can modify subscriber state here, e.g., set status to "pending" before persistence
        if (subscriber.getStatus() == null) {
            subscriber.setStatus("pending");
        }
        // Additional logic can be added here if needed
        return subscriber;
    }

    @PostMapping("/subscribers")
    public Subscriber signUp(@RequestBody @Valid SignUpRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received sign-up: {}", request.getEmail());
        // Check if email exists by querying all subscribers and filtering
        ArrayNode allSubs = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
        boolean exists = false;
        for (JsonNode node : allSubs) {
            String email = node.path("email").asText();
            if (email.equalsIgnoreCase(request.getEmail())) {
                exists = true;
                break;
            }
        }
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already subscribed");
        }
        Subscriber sub = new Subscriber();
        sub.setSubscriberId(UUID.randomUUID());
        sub.setEmail(request.getEmail());
        sub.setStatus("subscribed");

        // Use the new addItem API with workflow function
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                sub,
                this::processSubscriber
        );
        UUID technicalId = idFuture.get();
        sub.setTechnicalId(technicalId);
        logger.info("Subscriber created: {}", technicalId);
        return sub;
    }

    @PostMapping("/facts/sendWeekly")
    public Map<String, Object> sendWeeklyFact() {
        logger.info("Triggering weekly send");
        String factText;
        try {
            String resp = restTemplate.getForObject(new URI("https://catfact.ninja/fact"), String.class);
            JsonNode root = objectMapper.readTree(resp);
            factText = root.path("fact").asText();
        } catch (Exception e) {
            logger.error("Fetch failed", e);
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

        CompletableFuture.runAsync(() -> {
            logger.info("Sending to {} subs", count);
            // TODO: implement real email logic
            sentFacts.add(new SentFact(factText, Instant.now(), count));
        });
        Map<String, Object> out = new HashMap<>();
        out.put("sentCount", count);
        out.put("catFact", factText);
        out.put("timestamp", Instant.now().toString());
        return out;
    }

    @GetMapping("/subscribers")
    public List<Subscriber> getSubscribers() throws ExecutionException, InterruptedException {
        ArrayNode allSubs = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
        List<Subscriber> list = new ArrayList<>();
        for (JsonNode node : allSubs) {
            Subscriber sub = objectMapper.convertValue(node, Subscriber.class);
            // Map technicalId to subscriberId field as well for compatibility
            if (sub.getSubscriberId() == null && node.has("subscriberId")) {
                sub.setSubscriberId(UUID.fromString(node.get("subscriberId").asText()));
            }
            list.add(sub);
        }
        return list;
    }

    @GetMapping("/reports/summary")
    public Map<String, Object> getReportSummary() throws ExecutionException, InterruptedException {
        int totalSubs;
        try {
            ArrayNode allSubs = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).get();
            totalSubs = allSubs.size();
        } catch (Exception e) {
            totalSubs = 0;
        }
        int totalSent = sentFacts.size();
        long opens = interactions.values().stream().flatMap(List::stream)
                .filter(i -> "open".equals(i.getInteractionType())).count();
        long clicks = interactions.values().stream().flatMap(List::stream)
                .filter(i -> "click".equals(i.getInteractionType())).count();
        Instant last = sentFacts.stream().map(SentFact::getTimestamp)
                .max(Instant::compareTo).orElse(null);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalSubscribers", totalSubs);
        summary.put("totalFactsSent", totalSent);
        summary.put("totalEmailOpens", opens);
        summary.put("totalEmailClicks", clicks);
        summary.put("lastFactSentAt", last != null ? last.toString() : null);
        return summary;
    }

    @PostMapping("/interactions")
    public Map<String, Object> trackInteraction(@RequestBody @Valid InteractionRequest request) throws ExecutionException, InterruptedException {
        UUID sid;
        try {
            sid = UUID.fromString(request.getSubscriberId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID");
        }
        // verify subscriber exists
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
        return err;
    }
}
```
**Explanation:**

- Added a private method `processSubscriber(Subscriber subscriber)` that acts as the workflow function with the required naming convention (`process` + entity name).
- This method takes the entity, modifies it (you can add any logic here), and returns it.
- Updated the call to `entityService.addItem` in the `signUp` method to include the workflow function as the last parameter.
- The workflow function is passed as a method reference `this::processSubscriber`.
- No other business logic was changed; this matches the new API requirement for `addItem`.