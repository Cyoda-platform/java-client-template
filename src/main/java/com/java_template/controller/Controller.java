package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/cyoda/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class Controller {

    private final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdResponse> postItem(
            @RequestBody @NotNull ObjectNode data
    ) {
        logger.info("Received POST /cyoda/items request");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "entityModel",
                ENTITY_VERSION,
                data
        );

        UUID technicalId;
        try {
            technicalId = idFuture.join();
        } catch (Exception e) {
            logger.error("Error saving entity: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save entity");
        }

        logger.info("Entity stored successfully with technicalId {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new IdResponse(technicalId.toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObjectNode> getItem(
            @PathVariable("id")
            @Pattern(regexp = "[0-9a-fA-F\\-]{36}", message = "Invalid ID format") String id
    ) {
        logger.info("Received GET /cyoda/items/{} request", id);

        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format for id: {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "entityModel",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode item;
        try {
            item = itemFuture.join();
        } catch (Exception e) {
            logger.error("Error retrieving entity: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve entity");
        }

        if (item == null || !item.has("technicalId")) {
            logger.warn("Entity not found for technicalId {}", technicalId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity not found");
        }

        logger.info("Entity retrieved successfully for technicalId {}", technicalId);
        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: status={}, message={}", ex.getStatusCode(), ex.getReason());
        ErrorResponse errorResponse = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorResponse);
    }

    // Inner classes for responses
    public static class IdResponse {
        private String id;

        public IdResponse() {
        }

        public IdResponse(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse() {
        }

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}