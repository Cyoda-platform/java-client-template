package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/cyoda-entity-prototype", produces = MediaType.APPLICATION_JSON_VALUE)
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_MODEL = "prototype";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(path = "/items", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse storeItem(@RequestBody @Valid @NotNull ObjectNode data) {
        logger.info("Received POST /cyoda-entity-prototype/items request");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_MODEL,
                ENTITY_VERSION,
                data
        );

        UUID technicalId;
        try {
            technicalId = idFuture.join();
        } catch (Exception e) {
            logger.error("Failed to persist entity in addItem", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store entity");
        }

        logger.info("Stored entity with technicalId: {}", technicalId);
        return new IdResponse(technicalId.toString());
    }

    @GetMapping(path = "/items/{id}")
    public ObjectNode getItemById(@PathVariable("id") @NotBlank String id) {
        logger.info("Received GET /cyoda-entity-prototype/items/{} request", id);

        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for id: {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_MODEL,
                ENTITY_VERSION,
                uuid
        );

        ObjectNode item;
        try {
            item = itemFuture.join();
        } catch (Exception e) {
            logger.error("Failed to retrieve entity for id: {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve entity");
        }

        if (item == null) {
            logger.warn("Item not found for technicalId: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item with id " + id + " not found");
        }

        return item;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public ErrorResponse handleResponseStatusException(ResponseStatusException ex) {
        HttpStatus status = ex.getStatusCode();
        logger.error("Handled exception: status={}, message={}", status, ex.getReason());
        return new ErrorResponse(status.toString(), ex.getReason());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class IdResponse {
        @NotBlank
        private String id;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class ErrorResponse {
        @NotBlank
        private String error;
        @NotBlank
        private String message;
    }
}
