```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/user")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for cached user data: userId -> UserDetails
    private final Map<Integer, UserDetails> userCache = new ConcurrentHashMap<>();

    // Endpoint to trigger user retrieval from external ReqRes API and store internally
    @PostMapping("/retrieve")
    public ResponseEntity<ApiResponse> retrieveUser(@RequestBody UserRetrieveRequest request) {
        logger.info("Received request to retrieve user with ID {}", request.getUserId());

        if (request.getUserId() == null || request.getUserId() <= 0) {
            logger.error("Invalid userId provided: {}", request.getUserId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId must be positive integer");
        }

        Integer userId = request.getUserId();

        // Fire and forget retrieval to not block client - but here we do it synchronously for prototype
        try {
            JsonNode userData = fetchUserFromExternalApi(userId);
            if (userData == null) {
                logger.warn("User not found in external API: {}", userId);
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
            }

            UserDetails details = mapJsonToUserDetails(userData);
            userCache.put(userId, details);
            logger.info("User data cached for userId {}", userId);

            return ResponseEntity.ok(new ApiResponse("success", "User data retrieved and stored"));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving user {}", userId, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    // Endpoint to get stored user details by userId
    @GetMapping("/{userId}")
    public ResponseEntity<UserDetails> getUser(@PathVariable Integer userId) {
        logger.info("Received request to get stored user details for ID {}", userId);

        if (userId == null || userId <= 0) {
            logger.error("Invalid userId provided: {}", userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid userId must be positive integer");
        }

        UserDetails details = userCache.get(userId);
        if (details == null) {
            logger.warn("User data not found in cache for userId {}", userId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User data not found. Please retrieve it first.");
        }

        return ResponseEntity.ok(details);
    }

    // Fetch user JSON from ReqRes external API
    private JsonNode fetchUserFromExternalApi(Integer userId) {
        String url = "https://reqres.in/api/users/" + userId;
        logger.info("Calling external API: {}", url);

        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || dataNode.isNull()) {
                // User not found
                return null;
            }
            return dataNode;
        } catch (Exception ex) {
            logger.error("Error fetching user data from external API for userId {}", userId, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error calling external API");
        }
    }

    // Map external JSON user node to internal UserDetails model
    private UserDetails mapJsonToUserDetails(JsonNode node) {
        Integer id = node.path("id").asInt();
        String email = node.path("email").asText(null);
        String firstName = node.path("first_name").asText(null);
        String lastName = node.path("last_name").asText(null);
        String avatar = node.path("avatar").asText(null);

        return new UserDetails(id, email, firstName, lastName, avatar);
    }

    // Minimal error handler for ResponseStatusException to log errors
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("API error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiResponse("error", ex.getReason()));
    }

    // Request body for /retrieve POST
    @Data
    public static class UserRetrieveRequest {
        private Integer userId;
    }

    // Response wrapper for status messages
    @Data
    @AllArgsConstructor
    public static class ApiResponse {
        private String status;
        private String message;
    }

    // User details model matching ReqRes user fields we want to expose
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
```
