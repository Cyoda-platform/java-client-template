```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/user")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private static final String REQRES_API_URL = "https://reqres.in/api/users/";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for retrieved user data, keyed by userId
    private final Map<Integer, UserData> userStore = new ConcurrentHashMap<>();

    /**
     * POST /prototype/user/details
     * Accepts userId, retrieves user info from ReqRes API,
     * stores it locally for later retrieval.
     */
    @PostMapping("/details")
    public ResponseEntity<?> fetchAndStoreUserDetails(@RequestBody @NotNull UserIdRequest request) {
        logger.info("Received POST request to fetch user details for id={}", request.getUserId());

        if (request.getUserId() <= 0) {
            logger.error("Invalid userId provided: {}", request.getUserId());
            return ResponseEntity.badRequest().body(new ErrorResponse("error", "Invalid userId supplied"));
        }

        String url = REQRES_API_URL + request.getUserId();
        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);

            // Check if data node present and not null
            JsonNode dataNode = rootNode.get("data");
            if (dataNode == null || dataNode.isNull() || dataNode.isEmpty()) {
                logger.info("User not found at external API for userId={}", request.getUserId());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("error", "User not found"));
            }

            // Parse user data
            UserData userData = new UserData();
            userData.setId(dataNode.get("id").asInt());
            userData.setEmail(dataNode.get("email").asText());
            userData.setFirstName(dataNode.get("first_name").asText());
            userData.setLastName(dataNode.get("last_name").asText());
            userData.setAvatar(dataNode.get("avatar").asText());

            // Store user data in-memory
            userStore.put(userData.getId(), userData);
            logger.info("User data stored successfully for userId={}", userData.getId());

            return ResponseEntity.ok(new SuccessResponse("success", userData));

        } catch (ResponseStatusException ex) {
            logger.error("ResponseStatusException while fetching user data: {} - {}", ex.getStatusCode(), ex.getMessage());
            return ResponseEntity.status(ex.getStatusCode())
                    .body(new ErrorResponse("error", "User not found"));
        } catch (Exception ex) {
            logger.error("Exception while fetching user data", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("error", "Internal server error"));
        }
    }

    /**
     * GET /prototype/user/details/{userId}
     * Return user info previously stored by POST endpoint.
     */
    @GetMapping("/details/{userId}")
    public ResponseEntity<?> getUserDetails(@PathVariable int userId) {
        logger.info("Received GET request for user details with id={}", userId);

        if (userId <= 0) {
            logger.error("Invalid userId requested: {}", userId);
            return ResponseEntity.badRequest().body(new ErrorResponse("error", "Invalid userId supplied"));
        }

        UserData userData = userStore.get(userId);
        if (userData == null) {
            logger.warn("User data not found in store for userId={}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("error", "User data not found. Please POST to /prototype/user/details first."));
        }

        return ResponseEntity.ok(userData);
    }

    // Minimal error handler example for validation failures
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions() {
        return ResponseEntity.badRequest().body(new ErrorResponse("error", "Invalid request payload"));
    }

    // --- DTOs ---

    @Data
    public static class UserIdRequest {
        @NotNull
        private Integer userId;
    }

    @Data
    public static class UserData {
        private int id;
        private String email;
        private String firstName;
        private String lastName;
        private String avatar;
    }

    @Data
    @Getter
    @Setter
    public static class SuccessResponse {
        private String status;
        private UserData data;

        public SuccessResponse(String status, UserData data) {
            this.status = status;
            this.data = data;
        }
    }

    @Data
    @Getter
    @Setter
    public static class ErrorResponse {
        private String status;
        private String message;

        public ErrorResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}
```