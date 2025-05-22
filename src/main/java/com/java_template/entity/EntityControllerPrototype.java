package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/user")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Integer, UserDetails> userCache = new ConcurrentHashMap<>();

    @PostMapping("/retrieve")
    public ResponseEntity<ApiResponse> retrieveUser(
            @RequestBody @Valid UserRetrieveRequest request) {
        logger.info("Received request to retrieve user with ID {}", request.getUserId());
        Integer userId = request.getUserId();
        try {
            JsonNode userData = fetchUserFromExternalApi(userId);
            if (userData == null) {
                logger.warn("User not found in external API: {}", userId);
                throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND.getStatusCode(),"User not found");
            }
            UserDetails details = mapJsonToUserDetails(userData);
            userCache.put(userId, details);
            logger.info("User data cached for userId {}", userId);
            return ResponseEntity.ok(new ApiResponse("success", "User data retrieved and stored"));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Unexpected error retrieving user {}", userId, ex);
            throw new ResponseStatusException(ResponseStatusException.Status.INTERNAL_SERVER_ERROR.getStatusCode(),"Internal server error");
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDetails> getUser(
            @PathVariable @NotNull @Min(1) Integer userId) {
        logger.info("Received request to get stored user details for ID {}", userId);
        UserDetails details = userCache.get(userId);
        if (details == null) {
            logger.warn("User data not found in cache for userId {}", userId);
            throw new ResponseStatusException(ResponseStatusException.Status.NOT_FOUND.getStatusCode(),"User data not found. Please retrieve it first.");
        }
        return ResponseEntity.ok(details);
    }

    private JsonNode fetchUserFromExternalApi(Integer userId) {
        String url = "https://reqres.in/api/users/" + userId;
        logger.info("Calling external API: {}", url);
        try {
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || dataNode.isNull()) {
                return null;
            }
            return dataNode;
        } catch (Exception ex) {
            logger.error("Error fetching user data from external API for userId {}", userId, ex);
            throw new ResponseStatusException(ResponseStatusException.Status.INTERNAL_SERVER_ERROR.getStatusCode(),"Error calling external API");
        }
    }

    private UserDetails mapJsonToUserDetails(JsonNode node) {
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
        logger.error("API error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
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