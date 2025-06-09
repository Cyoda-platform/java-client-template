package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.AsyncRestTemplate;
import org.springframework.web.client.ResponseStatusException;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AsyncRestTemplate asyncRestTemplate = new AsyncRestTemplate();

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody @Valid User user) {
        if (users.containsKey(user.getEmail())) {
            logger.info("User with email {} already exists.", user.getEmail());
            return ResponseEntity.status(HttpStatus.CONFLICT).body("User already registered.");
        }
        users.put(user.getEmail(), user);
        logger.info("User with email {} registered successfully.", user.getEmail());
        return ResponseEntity.ok("User registered successfully.");
    }

    @GetMapping("/users")
    public ResponseEntity<Map<String, User>> getUsers() {
        logger.info("Retrieving all users.");
        return ResponseEntity.ok(users);
    }

    @PostMapping("/catfact")
    public CompletableFuture<ResponseEntity<String>> getCatFact() {
        return CompletableFuture.supplyAsync(() -> {
            ListenableFuture<ResponseEntity<String>> future = asyncRestTemplate.getForEntity(CAT_FACT_API_URL, String.class);
            future.addCallback(new ListenableFutureCallback<>() {
                @Override
                public void onSuccess(ResponseEntity<String> result) {
                    try {
                        JsonNode jsonNode = objectMapper.readTree(result.getBody());
                        String fact = jsonNode.get("fact").asText();
                        logger.info("Retrieved cat fact: {}", fact);
                    } catch (Exception e) {
                        logger.error("Failed to parse cat fact response.", e);
                    }
                }

                @Override
                public void onFailure(Throwable ex) {
                    logger.error("Failed to retrieve cat fact.", ex);
                }
            });
            return ResponseEntity.ok("Cat fact retrieval initiated.");
        });
    }

    @PostMapping("/sendEmails")
    public CompletableFuture<ResponseEntity<String>> sendEmails(@RequestBody @Valid CatFact catFact) {
        return CompletableFuture.runAsync(() -> {
            users.forEach((email, user) -> {
                // TODO: Implement actual email sending logic here
                logger.info("Sending cat fact to user with email: {}", email);
            });
        }).thenApply(result -> ResponseEntity.ok("Emails sent successfully."));
    }

    @GetMapping("/report")
    public ResponseEntity<Report> getReport() {
        int totalSubscribers = users.size();
        int emailsSent = totalSubscribers; // Placeholder logic
        String openRate = "75%"; // Placeholder logic

        Report report = new Report(totalSubscribers, emailsSent, openRate);
        logger.info("Generated report: {}", report);
        return ResponseEntity.ok(report);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getStatusCode().toString());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class User {
        @NotBlank
        @Email
        private String email;
        
        @NotBlank
        @Size(min = 2, max = 50)
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class CatFact {
        @NotBlank
        private String fact;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class Report {
        private int totalSubscribers;
        private int emailsSent;
        private String openRate;
    }
}