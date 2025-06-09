```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/data-retrieval")
    public CompletableFuture<ResponseEntity<JsonNode>> retrieveData(@RequestBody @Valid DataRetrievalRequest request) {
        logger.info("Retrieving data from external API: {}", request.getApiEndpoint());
        return entityService.getItemsByCondition("{entity_name}", ENTITY_VERSION, 
            SearchConditionRequest.group("AND", 
                Condition.of("$.field", "EQUALS", request.getParameters().get("value"))))
            .thenApply(data -> ResponseEntity.ok(data));
    }

    @PostMapping("/add-entity")
    public CompletableFuture<ResponseEntity<UUID>> addEntity(@RequestBody @Valid JsonNode entity) {
        logger.info("Adding new entity");
        return entityService.addItem("{entity_name}", ENTITY_VERSION, entity)
            .thenApply(technicalId -> ResponseEntity.ok(technicalId));
    }

    @GetMapping("/entities")
    public CompletableFuture<ResponseEntity<ArrayNode>> getEntities() {
        logger.info("Fetching all entities");
        return entityService.getItems("{entity_name}", ENTITY_VERSION)
            .thenApply(entities -> ResponseEntity.ok(entities));
    }

    @PutMapping("/update-entity/{id}")
    public CompletableFuture<ResponseEntity<UUID>> updateEntity(@PathVariable UUID id, @RequestBody @Valid JsonNode entity) {
        logger.info("Updating entity with ID: {}", id);
        return entityService.updateItem("{entity_name}", ENTITY_VERSION, id, entity)
            .thenApply(technicalId -> ResponseEntity.ok(technicalId));
    }

    @DeleteMapping("/delete-entity/{id}")
    public CompletableFuture<ResponseEntity<UUID>> deleteEntity(@PathVariable UUID id) {
        logger.info("Deleting entity with ID: {}", id);
        return entityService.deleteItem("{entity_name}", ENTITY_VERSION, id)
            .thenApply(technicalId -> ResponseEntity.ok(technicalId));
    }

    @DeleteMapping("/delete-all-entities")
    public CompletableFuture<ResponseEntity<ArrayNode>> deleteAllEntities() {
        logger.info("Deleting all entities");
        return entityService.deleteItems("{entity_name}", ENTITY_VERSION)
            .thenApply(deletedItemsInfo -> ResponseEntity.ok(deletedItemsInfo));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: {}", ex.getStatusCode().toString());
        return new ResponseEntity<>(ex.getStatusCode().toString(), ex.getStatusCode());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class DataRetrievalRequest {
        @NotBlank
        private String apiEndpoint;
        @NotNull
        private Map<String, String> parameters;
    }
}
```