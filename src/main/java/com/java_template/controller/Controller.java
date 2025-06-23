package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "Subscriber";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // --- CONTROLLER ENDPOINTS ---

    @PostMapping("/subscribers")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> subscribeUser(@RequestBody @Valid SubscriptionRequest request) {
        logger.info("Received subscription request for email={}", request.getEmail());
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Email is required");
        }

        ObjectNode subscriberNode = objectMapper.createObjectNode();
        subscriberNode.put("email", request.getEmail().trim().toLowerCase());
        if (request.getName() != null) subscriberNode.put("name", request.getName().trim());

        return entityService.addItem(ENTITY_NAME, ENTITY_VERSION, subscriberNode)
                .thenApply(id -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("subscriberId", id);
                    resp.put("message", "Subscription processed");
                    return ResponseEntity.ok(resp);
                });
    }

    @GetMapping("/subscribers")
    public CompletableFuture<List<Map<String, Object>>> getSubscribers() {
        logger.info("Retrieving all subscribers");
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        Map<String, Object> sub = new HashMap<>();
                        sub.put("id", node.path("technicalId").asText(null));
                        sub.put("email", node.path("email").asText(null));
                        sub.put("name", node.path("name").asText(null));
                        sub.put("subscribedAt", node.path("subscribedAt").asText(null));
                        list.add(sub);
                    }
                    logger.info("Retrieved {} subscribers", list.size());
                    return list;
                });
    }

    @PostMapping("/facts/send-weekly")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> sendWeeklyFact() {
        logger.info("Triggered weekly cat fact send");
        ObjectNode catFactNode = objectMapper.createObjectNode();
        catFactNode.put("fact", "");
        catFactNode.put("createdAt", Instant.now().toString());

        return entityService.addItem("CatFact", ENTITY_VERSION, catFactNode)
                .thenApply(id -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("factId", id);
                    resp.put("message", "Weekly cat fact processed and emails sent");
                    return ResponseEntity.ok(resp);
                });
    }

    @GetMapping("/reporting/metrics")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getReportingMetrics() {
        CompletableFuture<Integer> totalSubscribersFuture = entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenApply(nodes -> nodes.size());

        CompletableFuture<Integer> totalEmailsSentFuture = entityService.getItems("InteractionMetrics", ENTITY_VERSION)
                .thenApply(nodes -> {
                    int sum = 0;
                    for (JsonNode node : nodes) {
                        sum += node.path("emailsSent").asInt(0);
                    }
                    return sum;
                });

        CompletableFuture<Integer> totalEmailOpensFuture = entityService.getItems("InteractionMetrics", ENTITY_VERSION)
                .thenApply(nodes -> {
                    int sum = 0;
                    for (JsonNode node : nodes) {
                        sum += node.path("emailOpens").asInt(0);
                    }
                    return sum;
                });

        CompletableFuture<Integer> totalLinkClicksFuture = entityService.getItems("InteractionMetrics", ENTITY_VERSION)
                .thenApply(nodes -> {
                    int sum = 0;
                    for (JsonNode node : nodes) {
                        sum += node.path("linkClicks").asInt(0);
                    }
                    return sum;
                });

        return CompletableFuture.allOf(totalSubscribersFuture, totalEmailsSentFuture, totalEmailOpensFuture, totalLinkClicksFuture)
                .thenApply(v -> {
                    int totalSubscribers = totalSubscribersFuture.join();
                    int totalEmailsSent = totalEmailsSentFuture.join();
                    int totalEmailOpens = totalEmailOpensFuture.join();
                    int totalLinkClicks = totalLinkClicksFuture.join();

                    double averageOpenRate = totalEmailsSent > 0 ? (double) totalEmailOpens / totalEmailsSent : 0.0;
                    double averageClickRate = totalEmailsSent > 0 ? (double) totalLinkClicks / totalEmailsSent : 0.0;

                    Map<String, Object> metrics = new HashMap<>();
                    metrics.put("totalSubscribers", totalSubscribers);
                    metrics.put("totalEmailsSent", totalEmailsSent);
                    metrics.put("averageOpenRate", averageOpenRate);
                    metrics.put("averageClickRate", averageClickRate);

                    return ResponseEntity.ok(metrics);
                });
    }

    // --- REQUEST / RESPONSE DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionRequest {
        @NotBlank
        @Email
        private String email;

        @Size(max = 100)
        private String name;
    }
}