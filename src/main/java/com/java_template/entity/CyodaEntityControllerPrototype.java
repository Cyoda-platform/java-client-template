```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/prototype/api/pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/search") // must be first
    public CompletableFuture<ResponseEntity<?>> searchPets(@RequestBody @Valid SearchCriteria searchCriteria) {
        logger.info("Searching for pets with criteria: {}", searchCriteria);

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", searchCriteria.getStatus()),
                Condition.of("$.species", "EQUALS", searchCriteria.getSpecies()),
                Condition.of("$.categoryId", "EQUALS", searchCriteria.getCategoryId())
        );

        return entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition)
                .thenApply(items -> {
                    List<Pet> pets = items.findValues("technicalId").stream()
                            .map(idNode -> {
                                String id = idNode.asText();
                                JsonNode itemNode = items.findValue(id);
                                return new Pet(
                                        itemNode.get("name").asText(),
                                        itemNode.get("species").asText(),
                                        itemNode.get("status").asText(),
                                        itemNode.get("categoryId").asText(),
                                        itemNode.get("availabilityStatus").asText()
                                );
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(pets);
                });
    }

    @GetMapping("/results") // must be first
    public CompletableFuture<ResponseEntity<?>> getResults() {
        logger.info("Retrieving pet search results");

        return entityService.getItems("Pet", ENTITY_VERSION)
                .thenApply(items -> {
                    List<Pet> pets = items.findValues("technicalId").stream()
                            .map(idNode -> {
                                String id = idNode.asText();
                                JsonNode itemNode = items.findValue(id);
                                return new Pet(
                                        itemNode.get("name").asText(),
                                        itemNode.get("species").asText(),
                                        itemNode.get("status").asText(),
                                        itemNode.get("categoryId").asText(),
                                        itemNode.get("availabilityStatus").asText()
                                );
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(pets);
                });
    }

    @PostMapping("/notify") // must be first
    public ResponseEntity<?> notifyUser(@RequestBody @Valid Notification notification) {
        logger.info("Sending notification: {}", notification.getMessage());

        // TODO: Implement actual notification logic
        return ResponseEntity.ok(Map.of("status", "success", "notificationSent", true));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> errorResponse = Map.of("error", ex.getStatusCode().toString());
        logger.error("Handling exception: {}", ex.getStatusCode().toString());
        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class SearchCriteria {
        @NotBlank
        @Size(max = 50)
        private String species;

        @NotBlank
        @Pattern(regexp = "available|pending|sold", message = "Status must be one of: available, pending, sold")
        private String status;

        @NotBlank
        @Size(max = 10)
        private String categoryId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Pet {
        @NotBlank
        private String name;

        @NotBlank
        private String species;

        @NotBlank
        private String status;

        @NotBlank
        private String category;

        @NotBlank
        private String availabilityStatus;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class Notification {
        @NotBlank
        private String message;
    }
}
```