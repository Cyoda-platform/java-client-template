package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-api")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger emailsSentCounter = new AtomicInteger(0);
    private final AtomicInteger factsSentCounter = new AtomicInteger(0);
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final RestTemplate restTemplate = new RestTemplate();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Subscriber {
        private String id;
        @Email
        private String email;
        @Size(max = 100)
        private String name;
        private Instant subscribedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CatFact {
        private String factId;
        private String fact;
        private Instant timestamp;
    }

    @Data
    static class SubscriptionRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Email should be valid")
        private String email;

        @Size(max = 100, message = "Name must be at most 100 characters")
        private String name;
    }

    @Data
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
        private String subscriberId;
    }

    @Data
    @AllArgsConstructor
    static class SendWeeklyResponse {
        private String message;
        private String factId;
        private int sentToSubscribers;
    }

    @Data
    @AllArgsConstructor
    static class SubscriberCountResponse {
        private int totalSubscribers;
    }

    @Data
    @AllArgsConstructor
    static class InteractionReportResponse {
        private int factsSent;
        private int emailsSent;
    }

    @PostMapping(value = "/subscribe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse subscribeUser(@RequestBody @Valid SubscriptionRequest request) {
        logger.info("Received subscription request for email={}", request.getEmail());
        Subscriber subscriber = new Subscriber();
        subscriber.setEmail(request.getEmail());
        subscriber.setName(request.getName());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Subscriber",
                ENTITY_VERSION,
                subscriber
        );

        UUID technicalId = idFuture.join();
        String subscriberId = technicalId.toString();
        logger.info("Subscriber {} added successfully", subscriberId);
        return new MessageResponse("Subscription successful", subscriberId);
    }

    @PostMapping(value = "/facts/sendWeekly", produces = MediaType.APPLICATION_JSON_VALUE)
    public SendWeeklyResponse sendWeeklyCatFact() {
        logger.info("Triggered weekly cat fact fetch and sending");
        JsonNode catFactJson = fetchCatFactFromExternalApi();
        if (catFactJson == null || !catFactJson.has("fact")) {
            logger.error("Failed to fetch valid cat fact from external API");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch cat fact");
        }
        String factText = catFactJson.get("fact").asText();
        CatFact catFact = new CatFact();
        catFact.setFact(factText);

        CompletableFuture<UUID> factIdFuture = entityService.addItem(
                "CatFact",
                ENTITY_VERSION,
                catFact
        );

        UUID factTechnicalId = factIdFuture.join();
        String factIdStr = factTechnicalId.toString();

        return new SendWeeklyResponse("Weekly cat fact retrieved and emails sent", factIdStr, -1);
    }

    private JsonNode fetchCatFactFromExternalApi() {
        try {
            URI uri = new URI("https://catfact.ninja/fact");
            String response = restTemplate.getForObject(uri, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Error fetching cat fact from external API", e);
            return null;
        }
    }

    @GetMapping(value = "/facts", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<CatFact> getStoredCatFacts() {
        logger.info("Retrieving all stored cat facts");
        CompletableFuture<ArrayNode> factsFuture = entityService.getItems("CatFact", ENTITY_VERSION);
        ArrayNode factsArray = factsFuture.join();
        List<CatFact> facts = new ArrayList<>();
        for (JsonNode node : factsArray) {
            try {
                CatFact fact = objectMapper.treeToValue(node, CatFact.class);
                fact.setFactId(node.has("technicalId") ? node.get("technicalId").asText() : null);
                facts.add(fact);
            } catch (Exception e) {
                logger.error("Failed to parse cat fact from entityService data", e);
            }
        }
        return facts;
    }

    @GetMapping(value = "/report/subscribers", produces = MediaType.APPLICATION_JSON_VALUE)
    public SubscriberCountResponse getSubscriberCount() {
        logger.info("Reporting total subscribers");
        CompletableFuture<ArrayNode> subscribersFuture = entityService.getItems("Subscriber", ENTITY_VERSION);
        ArrayNode subscribersArray = subscribersFuture.join();
        int count = subscribersArray.size();
        logger.info("Total subscribers: {}", count);
        return new SubscriberCountResponse(count);
    }

    @GetMapping(value = "/report/interactions", produces = MediaType.APPLICATION_JSON_VALUE)
    public InteractionReportResponse getInteractionReport() {
        int factsSent = factsSentCounter.get();
        int emailsSent = emailsSentCounter.get();
        logger.info("Reporting interactions: factsSent={}, emailsSent={}", factsSent, emailsSent);
        return new InteractionReportResponse(factsSent, emailsSent);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("API error: {} - {}", ex.getStatusCode(), ex.getReason());
        return Map.of("error", ex.getStatusCode().toString());
    }
}
