```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/users/signup")
    public CompletableFuture<ResponseEntity<String>> signUp(@RequestBody @Valid EmailRequest emailRequest) {
        return entityService.addItem("subscriber", ENTITY_VERSION, emailRequest, this::processSubscriber)
                .thenApply(technicalId -> {
                    logger.info("User signed up with email: {}", emailRequest.getEmail());
                    return ResponseEntity.status(201).body("{\"message\": \"User signed up successfully.\"}");
                });
    }

    private CompletableFuture<JsonNode> processSubscriber(ObjectNode subscriberData) {
        // Implement any asynchronous processing logic here.
        logger.info("Processing subscriber data: {}", subscriberData);
        
        // Example: Fetch a cat fact and add it to the subscriber data
        return CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(CAT_FACT_API_URL, String.class);
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                String fact = jsonResponse.get("fact").asText();
                subscriberData.put("catFact", fact);  // Add additional data to the entity
                logger.info("Added cat fact to subscriber data: {}", fact);
            } catch (Exception e) {
                logger.error("Error retrieving cat fact", e);
            }
            return subscriberData;
        });
    }

    @PostMapping("/cat-facts/retrieve")
    public ResponseEntity<String> retrieveCatFact() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(CAT_FACT_API_URL, String.class);
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            String fact = jsonResponse.get("fact").asText();
            logger.info("Retrieved cat fact: {}", fact);
            return ResponseEntity.ok("{\"fact\": \"" + fact + "\"}");
        } catch (Exception e) {
            logger.error("Error retrieving cat fact", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @PostMapping("/cat-facts/send")
    public CompletableFuture<ResponseEntity<String>> sendWeeklyCatFact() {
        return entityService.getItems("subscriber", ENTITY_VERSION)
                .thenApply(subscribers -> {
                    logger.info("Sending weekly cat fact to all subscribers...");
                    subscribers.forEach(subscriber -> {
                        String email = subscriber.get("email").asText();
                        logger.info("Sent cat fact to: {}", email);
                    });
                    return ResponseEntity.ok("{\"message\": \"Weekly cat fact sent to all subscribers.\"}");
                });
    }

    @PostMapping("/users/unsubscribe")
    public CompletableFuture<ResponseEntity<String>> unsubscribe(@RequestBody @Valid EmailRequest emailRequest) {
        return entityService.getItemsByCondition(
                "subscriber",
                ENTITY_VERSION,
                SearchConditionRequest.group("AND", Condition.of("$.email", "EQUALS", emailRequest.getEmail())))
                .thenCompose(subscribers -> {
                    if (!subscribers.isEmpty()) {
                        UUID technicalId = subscribers.get(0).get("technicalId").asUUID();
                        return entityService.deleteItem("subscriber", ENTITY_VERSION, technicalId)
                                .thenApply(deletedId -> {
                                    logger.info("User unsubscribed with email: {}", emailRequest.getEmail());
                                    return ResponseEntity.ok("{\"message\": \"User unsubscribed successfully.\"}");
                                });
                    } else {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscriber not found");
                    }
                });
    }

    @GetMapping("/report/subscriber-count")
    public CompletableFuture<ResponseEntity<String>> getSubscriberCount() {
        return entityService.getItems("subscriber", ENTITY_VERSION)
                .thenApply(subscribers -> {
                    int count = subscribers.size();
                    logger.info("Number of subscribers: {}", count);
                    return ResponseEntity.ok("{\"count\": " + count + "}");
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body("{\"error\": \"" + ex.getStatusCode().toString() + "\"}");
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EmailRequest {
        @NotNull
        @NotBlank
        @Email
        private String email;
    }
}
```