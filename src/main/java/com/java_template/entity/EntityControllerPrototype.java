```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/users")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory cache: userId -> UserData JSON
    private final ConcurrentMap<Integer, JsonNode> userCache = new ConcurrentHashMap<>();

    // To track processing jobs (not used in current simple impl, but placeholder for async)
    private final ConcurrentMap<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String REQRES_URL = "https://reqres.in/api/users/";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    /**
     * POST /users/fetch
     * Accepts JSON: { "userId": 2 }
     * Fetches user info from ReqRes API and caches it.
     */
    @PostMapping("/fetch")
    public ResponseEntity<?> fetchUser(@RequestBody UserIdRequest request) {
        int userId = request.getUserId();
        log.info("Received request to fetch user with ID {}", userId);

        if (userId <= 0) {
            log.error("Invalid user ID: {}", userId);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }

        String url = REQRES_URL + userId;
        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);

            if (rootNode.has("data") && !rootNode.get("data").isNull()) {
                JsonNode userData = rootNode.get("data");
                userCache.put(userId, userData);
                log.info("User data cached for userId={}", userId);
                return ResponseEntity.ok(Map.of(
                        "message", "User data fetched successfully",
                        "userId", userId
                ));
            } else {
                log.warn("User not found in ReqRes API for userId={}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }
        } catch (Exception e) {
            log.error("Error fetching user data from ReqRes API for userId={}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch user data"));
        }
    }

    /**
     * GET /users/{userId}
     * Returns cached user info if present.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable int userId) {
        log.info("Received request to get user with ID {}", userId);

        JsonNode userData = userCache.get(userId);
        if (userData == null) {
            log.warn("User data not found in cache for userId={}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User data not found. Please fetch first."));
        }
        return ResponseEntity.ok(userData);
    }

    /**
     * Minimal error handling for unexpected exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    // --- DTOs and helper classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserIdRequest {
        private int userId;
    }

    @Data
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    // TODO: If async fetch or background jobs are needed, implement async methods and job tracking here.
}
```
