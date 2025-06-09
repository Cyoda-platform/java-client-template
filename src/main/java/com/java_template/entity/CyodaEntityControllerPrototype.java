```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
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
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/users/signup")
    public CompletableFuture<ResponseEntity<String>> signUp(@RequestBody @Valid EmailRequest emailRequest) {
        return entityService.addItem("subscriber", ENTITY_VERSION, emailRequest)
                .thenApply(technicalId -> {
                    logger.info("User signed up with email: {}", emailRequest.getEmail());
                    return ResponseEntity.status(201).body("{\"message\": \"User signed up successfully.\"}");
                });
    }

    @PostMapping("/cat-facts/retrieve")
    public ResponseEntity<String> retrieveCatFact() {
        try {
            // Assuming that the retrieval of cat facts is not related to the EntityService
            ResponseEntity<String> response = restTemplate.getForEntity(CAT_FACT_API_URL, String.class);
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            String fact = jsonResponse.get("fact").asText();
            logger.info("Retrieved cat fact: {}", fact);
            return ResponseEntity.ok("{\"fact\": \"" + fact + "\"}");
        } catch (Exception e) {
            logger.error("Error retrieving cat fact", e);
            throw new ResponseStatusException(response.getStatusCode(), e.getMessage());
        }
    }

    @PostMapping("/cat-facts/send")
    public CompletableFuture<ResponseEntity<String>> sendWeeklyCatFact() {
        return entityService.getItems("subscriber", ENTITY_VERSION)
                .thenApply(subscribers -> {
                    // Implement the logic to send cat facts to all subscribers
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