package com.java_template.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping(path = "/cyoda/prototype/items", produces = MediaType.APPLICATION_JSON_VALUE)
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IdResponse> saveItem(@RequestBody @Valid ObjectNode data) {
        logger.info("POST /cyoda/prototype/items - received save request");

        if (data.isEmpty(null)) {
            logger.error("Empty entity data");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Empty entity data");
        }

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "prototype",
                ENTITY_VERSION,
                data
        );

        UUID technicalId;
        try {
            technicalId = idFuture.join();
        } catch (Exception e) {
            logger.error("Failed to persist entity", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to persist entity");
        }

        logger.info("Item stored with technicalId {}", technicalId);
        return ResponseEntity.ok(new IdResponse(technicalId.toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObjectNode> getItem(
            @PathVariable("id") @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$") String id) {
        logger.info("GET /cyoda/prototype/items/{} - retrieving item", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "prototype",
                ENTITY_VERSION,
                UUID.fromString(id)
        );

        ObjectNode item;
        try {
            item = itemFuture.join();
        } catch (Exception e) {
            logger.error("Failed to retrieve entity with id {}", id, e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve entity");
        }

        if (item == null) {
            logger.warn("Item with id {} not found", id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Item not found");
        }
        return ResponseEntity.ok(item);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error occurred: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class IdResponse {
        private String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ErrorResponse {
        private String error;
        private String message;
    }
}