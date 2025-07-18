package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/user")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String REQRES_API_URL = "https://reqres.in/api/users/";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Integer, UserData> userStore = new ConcurrentHashMap<>();

    @PostMapping("/details")
    public ResponseEntity<?> fetchAndStoreUserDetails(@RequestBody @Valid UserIdRequest request) {
        logger.info("Received POST request to fetch user details for id={}", request.getUserId());
        String url = REQRES_API_URL + request.getUserId();
        try {
            String rawResponse = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            JsonNode dataNode = rootNode.get("data");
            if (dataNode == null || dataNode.isNull()) {
                logger.info("User not found at external API for userId={}", request.getUserId());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("error", "User not found"));
            }
            UserData userData = new UserData();
            userData.setId(dataNode.get("id").asInt());
            userData.setEmail(dataNode.get("email").asText());
            userData.setFirstName(dataNode.get("first_name").asText());
            userData.setLastName(dataNode.get("last_name").asText());
            userData.setAvatar(dataNode.get("avatar").asText());
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

    @GetMapping("/details/{userId}")
    public ResponseEntity<?> getUserDetails(@PathVariable @Min(1) int userId) {
        logger.info("Received GET request for user details with id={}", userId);
        UserData userData = userStore.get(userId);
        if (userData == null) {
            logger.warn("User data not found in store for userId={}", userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("error", "User data not found. Please POST to /prototype/user/details first."));
        }
        return ResponseEntity.ok(userData);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        logger.error("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("error", "Invalid request payload"));
    }

    @Data
    public static class UserIdRequest {
        @NotNull
        @Min(1)
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
    public static class SuccessResponse {
        private String status;
        private UserData data;
        public SuccessResponse(String status, UserData data) {
            this.status = status;
            this.data = data;
        }
    }

    @Data
    public static class ErrorResponse {
        private String status;
        private String message;
        public ErrorResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}