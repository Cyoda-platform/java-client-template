package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda/api")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/data-retrieval")
    public CompletableFuture<ResponseEntity<JsonNode>> retrieveData(@RequestBody @Valid DataRetrievalRequest request) {
        logger.info("Retrieving data from external API: {}", request.getApiEndpoint());
        return entityService.getItemsByCondition("{entity_name}", ENTITY_VERSION,
            SearchConditionRequest.group("AND",
                Condition.of("$.field", "EQUALS", request.getParameters().get("value"))))
            .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/add-entity")
    public CompletableFuture<ResponseEntity<UUID>> addEntity(@RequestBody @Valid JsonNode entity) {
        logger.info("Adding new entity");
        return entityService.addItem("{entity_name}", ENTITY_VERSION, entity)
            .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/entities")
    public CompletableFuture<ResponseEntity<ArrayNode>> getEntities() {
        logger.info("Fetching all entities");
        return entityService.getItems("{entity_name}", ENTITY_VERSION)
            .thenApply(ResponseEntity::ok);
    }

    @PutMapping("/update-entity/{id}")
    public CompletableFuture<ResponseEntity<UUID>> updateEntity(@PathVariable UUID id, @RequestBody @Valid JsonNode entity) {
        logger.info("Updating entity with ID: {}", id);
        return entityService.updateItem("{entity_name}", ENTITY_VERSION, id, entity)
            .thenApply(ResponseEntity::ok);
    }

    @DeleteMapping("/delete-entity/{id}")
    public CompletableFuture<ResponseEntity<UUID>> deleteEntity(@PathVariable UUID id) {
        logger.info("Deleting entity with ID: {}", id);
        return entityService.deleteItem("{entity_name}", ENTITY_VERSION, id)
            .thenApply(ResponseEntity::ok);
    }

    @DeleteMapping("/delete-all-entities")
    public CompletableFuture<ResponseEntity<ArrayNode>> deleteAllEntities() {
        logger.info("Deleting all entities");
        return entityService.deleteItems("{entity_name}", ENTITY_VERSION)
            .thenApply(ResponseEntity::ok);
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