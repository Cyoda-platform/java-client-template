package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Slf4j
@Validated
@RestController
@RequestMapping("/users")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<Integer, JsonNode> userCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, JobStatus> entityJobs = new ConcurrentHashMap<>();
    private static final String REQRES_URL = "https://reqres.in/api/users/";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/fetch")
    public ResponseEntity<?> fetchUser(@RequestBody @Valid UserIdRequest request) {
        int userId = request.getUserId();
        log.info("Received request to fetch user with ID {}", userId);
        if (userId <= 0) {
            log.error("Invalid user ID: {}", userId);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }
        try {
            String rawResponse = restTemplate.getForObject(REQRES_URL + userId, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            if (rootNode.has("data") && !rootNode.get("data").isNull()) {
                JsonNode userData = rootNode.get("data");
                userCache.put(userId, userData);
                log.info("User data cached for userId={}", userId);
                return ResponseEntity.ok(Map.of("message", "User data fetched successfully", "userId", userId));
            } else {
                log.warn("User not found in ReqRes API for userId={}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "User not found"));
            }
        } catch (Exception e) {
            log.error("Error fetching user data for userId={}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch user data"));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUser(@PathVariable @Min(value = 1, message = "userId must be ≥ 1") int userId) {
        log.info("Received request to get user with ID {}", userId);
        JsonNode userData = userCache.get(userId);
        if (userData == null) {
            log.warn("User data not found in cache for userId={}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User data not found. Please fetch first."));
        }
        return ResponseEntity.ok(userData);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserIdRequest {
        @Min(value = 1, message = "userId must be greater than or equal to 1")
        private int userId;
    }

    @Data
    @AllArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
    }
}