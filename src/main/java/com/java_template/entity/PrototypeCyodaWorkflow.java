package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-user")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "user";

    public CyodaEntityControllerPrototype(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/retrieve")
    public CompletableFuture<ResponseEntity<ApiResponse>> retrieveUser(@RequestBody @Valid UserRetrieveRequest request) {
        logger.info("Received request to retrieve user with ID {}", request.getUserId());
        Integer userId = request.getUserId();

        // Fetch user data from external API asynchronously
        return fetchUserFromExternalApiAsync(userId)
                .thenCompose(userData -> {
                    if (userData == null) {
                        logger.warn("User not found in external API: {}", userId);
                        CompletableFuture<ResponseEntity<ApiResponse>> cf = new CompletableFuture<>();
                        cf.completeExceptionally(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
                        return cf;
                    }
                    // Add entity with workflow function that enriches entity before persistence
                    return entityService.addItem(
                            ENTITY_NAME,
                            ENTITY_VERSION,
                            userData,
                            this::processUser
                    ).thenApply(technicalId -> {
                        logger.info("User data stored with technicalId {}", technicalId);
                        return ResponseEntity.ok(new ApiResponse("success", "User data retrieved and stored"));
                    });
                });
    }

    /**
     * Workflow function to process user entity (ObjectNode) before persistence.
     * This function is async and returns CompletableFuture<ObjectNode>.
     * You can mutate entity state directly, enrich data, fetch/add supplementary entities of different models.
     */
    private CompletableFuture<ObjectNode> processUser(ObjectNode entity) {
        logger.info("Processing user entity in workflow before persistence: id={}", entity.path("id").asText("N/A"));

        // Mutate entity directly: add a timestamp field
        entity.put("retrievedAt", Instant.now().toString());

        // Example asynchronous supplementary data addition
        CompletableFuture<Void> supplementaryDataFuture = CompletableFuture.runAsync(() -> {
            try {
                logger.info("Simulating async fetch of supplementary data for user id={}", entity.path("id").asInt());
                // Example: add a supplementary entity of different model "userRole"
                ObjectNode userRole = objectMapper.createObjectNode();
                userRole.put("userId", entity.path("id").asInt());
                userRole.put("role", "basic-user");
                // Use identity workflow to avoid infinite recursion for supplementary entity
                entityService.addItem("userRole", ENTITY_VERSION, userRole, Function.identity());
            } catch (Exception ex) {
                logger.error("Error adding supplementary userRole entity", ex);
                // Failure here should not stop main persistence, just log error
            }
        });

        // Return the entity after supplementary data addition completes
        return supplementaryDataFuture.thenApply(v -> entity);
    }

    /**
     * Async fetch user data from external API as ObjectNode.
     */
    private CompletableFuture<ObjectNode> fetchUserFromExternalApiAsync(Integer userId) {
        return CompletableFuture.supplyAsync(() -> {
            String url = "https://reqres.in/api/users/" + userId;
            logger.info("Calling external API: {}", url);
            try {
                org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(response);
                JsonNode dataNode = root.get("data");
                if (dataNode == null || dataNode.isNull()) {
                    return null;
                }
                return (ObjectNode) dataNode;
            } catch (Exception ex) {
                logger.error("Error fetching user data from external API for userId {}", userId, ex);
                throw new RuntimeException("Error calling external API", ex);
            }
        });
    }

    @GetMapping("/{userId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> getUser(@PathVariable @NotNull @Min(1) Integer userId) {
        logger.info("Received request to get stored user details for ID {}", userId);

        String condition = String.format("id == %d", userId);
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
                .thenApply(items -> {
                    if (items == null || items.isEmpty()) {
                        logger.warn("User data not found for userId {}", userId);
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User data not found. Please retrieve it first.");
                    }
                    return (ObjectNode) items.get(0);
                })
                .thenApply(ResponseEntity::ok);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("API error: {} - {}", ex.getStatus(), ex.getReason());
        return ResponseEntity.status(ex.getStatus())
                .body(new ApiResponse("error", ex.getReason()));
    }

    @Data
    public static class UserRetrieveRequest {
        @NotNull
        @Min(1)
        private Integer userId;
    }

    @Data
    @AllArgsConstructor
    public static class ApiResponse {
        private String status;
        private String message;
    }
}