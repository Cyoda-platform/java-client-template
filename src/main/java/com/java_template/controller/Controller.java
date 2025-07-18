package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Validated
@RestController
@RequestMapping(path = "/prototype/user")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private static final String REQRES_API_URL = "https://reqres.in/api/users/";
    private static final String ENTITY_NAME = "User";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public Controller(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

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

            // Add userData to external entity service
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    userData
            );
            UUID technicalId = idFuture.get(); // wait for completion

            logger.info("User data stored successfully with technicalId={}", technicalId);
            return ResponseEntity.ok(new SuccessResponse("success", userData));
        } catch (ExecutionException | InterruptedException ex) {
            logger.error("Error while storing user data", ex);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("error", "Internal server error"));
        } catch (Exception ex) {
            logger.error("Exception while fetching user data", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("error", "Internal server error"));
        }
    }

    @GetMapping("/details/{userId}")
    public ResponseEntity<?> getUserDetails(@PathVariable @Min(1) int userId) {
        logger.info("Received GET request for user details with id={}", userId);
        try {
            // Create condition to find user by id field
            var condition = com.java_template.common.util.SearchConditionRequest.group("AND",
                    com.java_template.common.util.Condition.of("$.id", "EQUALS", userId));

            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    ENTITY_NAME,
                    com.java_template.common.config.Config.ENTITY_VERSION,
                    condition
            );

            com.fasterxml.jackson.databind.node.ArrayNode results = itemsFuture.get();
            if (results == null || results.isEmpty()) {
                logger.warn("User data not found in external service for userId={}", userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("error", "User data not found. Please POST to /prototype/user/details first."));
            }

            JsonNode userNode = results.get(0);
            // Convert JsonNode to UserData
            UserData userData = objectMapper.treeToValue(userNode, UserData.class);

            return ResponseEntity.ok(userData);
        } catch (ExecutionException | InterruptedException ex) {
            logger.error("Error while retrieving user data", ex);
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("error", "Internal server error"));
        } catch (Exception ex) {
            logger.error("Exception while retrieving user data", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("error", "Internal server error"));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        logger.error("Validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("error", "Invalid request payload"));
    }

    public static class UserIdRequest {
        @NotNull
        @Min(1)
        private Integer userId;

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }
    }

    public static class UserData {
        private int id;
        private String email;
        private String firstName;
        private String lastName;
        private String avatar;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getAvatar() {
            return avatar;
        }

        public void setAvatar(String avatar) {
            this.avatar = avatar;
        }
    }

    public static class SuccessResponse {
        private String status;
        private UserData data;

        public SuccessResponse(String status, UserData data) {
            this.status = status;
            this.data = data;
        }

        public String getStatus() {
            return status;
        }

        public UserData getData() {
            return data;
        }
    }

    public static class ErrorResponse {
        private String status;
        private String message;

        public ErrorResponse(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}