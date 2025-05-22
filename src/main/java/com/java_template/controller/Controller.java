package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyoda-entity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/books")
    public CompletableFuture<UUID> addBook(@Valid @RequestBody ObjectNode bookEntity) {
        return entityService.addItem("book", ENTITY_VERSION, bookEntity);
    }

    @PostMapping("/books/batch")
    public CompletableFuture<List<UUID>> addBooks(@Valid @RequestBody ArrayNode bookEntities) {
        List<ObjectNode> entities = new ArrayList<>();
        for (JsonNode node : bookEntities) {
            if (node instanceof ObjectNode) {
                entities.add((ObjectNode) node);
            }
        }
        return entityService.addItems("book", ENTITY_VERSION, entities);
    }

    @GetMapping(value = "/books/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ObjectNode> getBook(@PathVariable("id") UUID id) {
        return entityService.getItem("book", ENTITY_VERSION, id)
                .thenApply(entity -> {
                    if (entity == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
                    }
                    ((ObjectNode) entity).remove("technicalId");
                    return (ObjectNode) entity;
                });
    }

    @GetMapping(value = "/books", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ArrayNode> getAllBooks() {
        return entityService.getItems("book", ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    for (JsonNode node : arrayNode) {
                        if (node instanceof ObjectNode) {
                            ((ObjectNode) node).remove("technicalId");
                        }
                    }
                    return arrayNode;
                });
    }

    @PutMapping("/books/{id}")
    public CompletableFuture<UUID> updateBook(@PathVariable("id") UUID id, @Valid @RequestBody ObjectNode bookEntity) {
        return entityService.updateItem("book", ENTITY_VERSION, id, bookEntity);
    }

    @DeleteMapping("/books/{id}")
    public CompletableFuture<UUID> deleteBook(@PathVariable("id") UUID id) {
        return entityService.deleteItem("book", ENTITY_VERSION, id);
    }

    @PostMapping("/report")
    public CompletableFuture<UUID> addReport(@Valid @RequestBody ObjectNode reportEntity) {
        return entityService.addItem("bookReport", ENTITY_VERSION, reportEntity);
    }

    @GetMapping(value = "/report/{reportId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ObjectNode> getReport(@PathVariable("reportId") @NotBlank String reportId) {
        return entityService.getItem("bookReport", ENTITY_VERSION, reportId)
                .thenApply(entity -> {
                    if (entity == null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
                    }
                    ((ObjectNode) entity).remove("technicalId");
                    return (ObjectNode) entity;
                });
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        return Map.of(
                "status", ex.getStatusCode().value(),
                "error", ex.getReason()
        );
    }

    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        return Map.of(
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Internal server error"
        );
    }
}