package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda-users")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;

    private static final String ENTITY_NAME = "user";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/fetch")
    public ResponseEntity<?> fetchUser(@RequestBody @Valid UserIdRequest request) throws ExecutionException, InterruptedException {
        int userId = request.getUserId();
        logger.info("Received request to fetch user with ID {}", userId);
        if (userId <= 0) {
            logger.error("Invalid user ID: {}", userId);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID"));
        }

        // Call external API to fetch user data
        // Using RestTemplate is not allowed to be replaced, so keep fetching external API logic here
        // but after validation, store the user data in entityService

        // Since the original logic uses RestTemplate directly, keep that part here for data ingestion
        // Then store the fetched JsonNode into entityService

        // For this example, keep RestTemplate and ObjectMapper here for fetching external data only
        // But for storing, replace local cache with entityService.addItem

        var restTemplate = new org.springframework.web.client.RestTemplate();
        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        String REQRES_URL = "https://reqres.in/api/users/";

        var rawResponse = restTemplate.getForObject(REQRES_URL + userId, String.class);
        var rootNode = objectMapper.readTree(rawResponse);
        if (rootNode.has("data") && !rootNode.get("data").isNull()) {
            JsonNode userData = rootNode.get("data");
            // Add userData to entityService
            UUID technicalId = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, userData).get();
            logger.info("User data stored with technicalId={}", technicalId);
            return ResponseEntity.ok(Map.of("message", "User data fetched and stored successfully", "userId", userId, "technicalId", technicalId));
        } else {
            logger.warn("User not found in ReqRes API for userId={}", userId);
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
    }

    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getUser(@PathVariable UUID technicalId) throws ExecutionException, InterruptedException {
        logger.info("Received request to get user with technicalId {}", technicalId);
        ObjectNode userData = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId).get();
        if (userData == null || userData.isEmpty()) {
            logger.warn("User data not found for technicalId={}", technicalId);
            return ResponseEntity.status(404).body(Map.of("error", "User data not found. Please fetch first."));
        }
        return ResponseEntity.ok(userData);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserIdRequest {
        @Min(value = 1, message = "userId must be greater than or equal to 1")
        private int userId;
    }
}