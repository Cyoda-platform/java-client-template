```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype/api")
@Validated
public class EntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<Integer, User> userStore = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/retrieve-user") // must be first
    public ResponseEntity<User> retrieveUser(@RequestBody @Valid UserIdRequest request) {
        try {
            String url = "https://reqres.in/api/users/" + request.getUserId();
            logger.info("Fetching user from ReqRes API with URL: {}", url);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            JsonNode rootNode = objectMapper.readTree(response.getBody());
            JsonNode dataNode = rootNode.path("data");

            if (dataNode.isMissingNode()) {
                logger.error("User not found for ID: {}", request.getUserId());
                return ResponseEntity.status(response.getStatusCode()).body(null);
            }

            User user = objectMapper.treeToValue(dataNode, User.class);
            userStore.put(user.getId(), user);
            logger.info("User retrieved and stored: {}", user);

            return ResponseEntity.ok(user);

        } catch (ResponseStatusException e) {
            logger.error("Error fetching user: {}", e.getStatusCode().toString());
            return ResponseEntity.status(e.getStatusCode()).body(null);
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/user-details") // must be first
    public ResponseEntity<User> getUserDetails(@RequestParam @NotNull Integer userId) {
        User user = userStore.get(userId);
        if (user == null) {
            logger.error("User not found in store for ID: {}", userId);
            return ResponseEntity.status(404).body(null);
        }
        logger.info("User found in store: {}", user);
        return ResponseEntity.ok(user);
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class User {
    @NotNull
    private Integer id;

    @NotBlank
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(max = 50)
    private String first_name;

    @NotBlank
    @Size(max = 50)
    private String last_name;

    @NotBlank
    @Size(max = 200)
    private String avatar;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class UserIdRequest {
    @NotNull
    private Integer userId;
}
```