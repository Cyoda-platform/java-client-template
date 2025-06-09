package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/cyoda/entity")
@Validated
public class CyodaEntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/retrieve-user")
    public CompletableFuture<ResponseEntity<User>> retrieveUser(@RequestBody @Valid UserIdRequest request) {
        try {
            String url = "https://reqres.in/api/users/" + request.getUserId();
            logger.info("Fetching user from ReqRes API with URL: {}", url);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    "User", ENTITY_VERSION, UUID.fromString(request.getUserId().toString()));

            return itemFuture.thenCompose(dataNode -> {
                if (dataNode == null) {
                    logger.error("User not found for ID: {}", request.getUserId());
                    return CompletableFuture.completedFuture(ResponseEntity.status(404).body(null));
                }

                // Add the item without workflow function
                return entityService.addItem(
                        "User",
                        ENTITY_VERSION,
                        dataNode
                ).thenApply(id -> {
                    User user = objectMapper.convertValue(dataNode, User.class);
                    logger.info("User retrieved and processed: {}", user);
                    return ResponseEntity.ok(user);
                });

            });

        } catch (ResponseStatusException e) {
            logger.error("Error fetching user: {}", e.getStatusCode().toString());
            return CompletableFuture.completedFuture(ResponseEntity.status(e.getStatusCode()).body(null));
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            return CompletableFuture.completedFuture(ResponseEntity.status(500).body(null));
        }
    }

    @GetMapping("/user-details")
    public CompletableFuture<ResponseEntity<User>> getUserDetails(@RequestParam @NotNull Integer userId) {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", userId.toString()));

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                "User", ENTITY_VERSION, condition);

        return filteredItemsFuture.thenApply(items -> {
            if (items.isEmpty()) {
                logger.error("User not found in store for ID: {}", userId);
                return ResponseEntity.status(404).body(null);
            }

            User user = objectMapper.convertValue(items.get(0), User.class);
            logger.info("User found in store: {}", user);
            return ResponseEntity.ok(user);
        });
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
