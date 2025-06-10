package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/api")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Constructor injection of EntityService and ObjectMapper
    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/signup")
    public CompletableFuture<ResponseEntity<?>> signUp(@RequestBody @Valid Subscriber subscriber) {
        logger.info("Received sign-up request for email: {}", subscriber.getEmail());
        return entityService.addItem("Subscriber", ENTITY_VERSION, subscriber)
                .thenApply(technicalId -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("message", "User successfully signed up", "technicalId", technicalId.toString())));
    }

    @PostMapping("/catfact")
    public ResponseEntity<?> getCatFact() {
        logger.info("Fetching a new cat fact");
        String apiUrl = "https://catfact.ninja/fact";
        try {
            String response = new RestTemplate().getForObject(apiUrl, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            String fact = jsonNode.get("fact").asText();
            return ResponseEntity.ok(Map.of("fact", fact));
        } catch (Exception e) {
            logger.error("Error fetching cat fact: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve cat fact");
        }
    }

    @PostMapping("/sendWeeklyEmail")
    public CompletableFuture<ResponseEntity<?>> sendWeeklyEmail() {
        logger.info("Initiating weekly email send-out");

        return entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<JsonNode> subscribers = arrayNode.findValues("email");
                    subscribers.forEach(subscriber -> {
                        // TODO: Implement actual email sending logic here
                        logger.info("Sending email to {}", subscriber.asText());
                    });
                    return ResponseEntity.ok(Map.of("message", "Weekly emails sent to all subscribers"));
                });
    }

    @GetMapping("/stats/subscribers")
    public CompletableFuture<ResponseEntity<?>> getSubscriberStats() {
        logger.info("Retrieving subscriber stats");

        return entityService.getItems("Subscriber", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    int totalSubscribers = arrayNode.size();
                    int opens = (int) (totalSubscribers * 0.8); // Mock 80% open rate
                    int clicks = (int) (totalSubscribers * 0.6); // Mock 60% click rate

                    return ResponseEntity.ok(Map.of(
                            "totalSubscribers", totalSubscribers,
                            "weeklyInteractions", Map.of("opens", opens, "clicks", clicks)
                    ));
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getStatusCode().toString()));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Subscriber {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 1, max = 100)
        private String name;
    }
}