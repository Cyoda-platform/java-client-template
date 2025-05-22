package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/api/cyoda-user")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_NAME = "user";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/retrieve")
    public ResponseEntity<ApiResponse> retrieveUser(@RequestBody @Valid UserRetrieveRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received request to retrieve user with ID {}", request.getUserId());
        Integer userId = request.getUserId();
        // Fetch user data from external API
        ObjectNode userData = fetchUserFromExternalApi(userId);
        if (userData == null) {
            logger.warn("User not found in external API: {}", userId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found");
        }
        // Map to UserDetails
        UserDetails details = mapJsonToUserDetails(userData);
        // Add to entityService
        UUID technicalId = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, details).get();
        logger.info("User data stored with technicalId {}", technicalId);
        return ResponseEntity.ok(new ApiResponse("success", "User data retrieved and stored"));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDetails> getUser(@PathVariable @NotNull @Min(1) Integer userId) throws ExecutionException, InterruptedException {
        logger.info("Received request to get stored user details for ID {}", userId);
        // Use getItemsByCondition to filter by id field
        String condition = String.format("id == %d", userId);
        ArrayNode items = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition).get();
        if (items == null || items.isEmpty()) {
            logger.warn("User data not found for userId {}", userId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User data not found. Please retrieve it first.");
        }
        // Extract first matching item
        ObjectNode userObject = (ObjectNode) items.get(0);
        // Map ObjectNode to UserDetails
        UserDetails details = objectNodeToUserDetails(userObject);
        return ResponseEntity.ok(details);
    }

    private ObjectNode fetchUserFromExternalApi(Integer userId) {
        // Use RestTemplate as before to call external API
        // This method is copied from original controller with no cache usage
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String url = "https://reqres.in/api/users/" + userId;
        logger.info("Calling external API: {}", url);
        try {
            String response = restTemplate.getForObject(url, String.class);
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(response);
            com.fasterxml.jackson.databind.JsonNode dataNode = root.get("data");
            if (dataNode == null || dataNode.isNull()) {
                return null;
            }
            return (ObjectNode) dataNode;
        } catch (Exception ex) {
            logger.error("Error fetching user data from external API for userId {}", userId, ex);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error calling external API");
        }
    }

    private UserDetails mapJsonToUserDetails(com.fasterxml.jackson.databind.JsonNode node) {
        return new UserDetails(
                node.path("id").asInt(),
                node.path("email").asText(null),
                node.path("first_name").asText(null),
                node.path("last_name").asText(null),
                node.path("avatar").asText(null)
        );
    }

    private UserDetails objectNodeToUserDetails(ObjectNode node) {
        return new UserDetails(
                node.path("id").asInt(),
                node.path("email").asText(null),
                node.path("first_name").asText(null),
                node.path("last_name").asText(null),
                node.path("avatar").asText(null)
        );
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

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserDetails {
        private Integer id;
        private String email;
        private String first_name;
        private String last_name;
        private String avatar;
    }
}