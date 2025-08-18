package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.java_template.common.service.EntityService;
import com.java_template.application.entity.changeevent.version_1.ChangeEvent;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/changeevents")
@Tag(name = "ChangeEvent Controller", description = "Read-only access for change events. ChangeEvents are created by system workflows.")
public class ChangeEventController {
    private final Logger logger = LoggerFactory.getLogger(ChangeEventController.class);
    private final EntityService entityService;

    public ChangeEventController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get ChangeEvent by technicalId", description = "Retrieve a ChangeEvent by its technicalId")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getChangeEvent(@Parameter(name = "technicalId", description = "Technical ID of the change event") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ChangeEvent.ENTITY_NAME,
                    String.valueOf(ChangeEvent.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid getChangeEvent request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            if (cause instanceof IllegalArgumentException) return ResponseEntity.badRequest().body(cause.getMessage());
            logger.error("Execution error fetching change event", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching change event", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error fetching change event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List ChangeEvents", description = "List all change events")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    })
    @GetMapping
    public ResponseEntity<?> listChangeEvents() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    ChangeEvent.ENTITY_NAME,
                    String.valueOf(ChangeEvent.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution error listing change events", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing change events", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error listing change events", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Filter ChangeEvents", description = "Filter change events by simple field condition")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ArrayNode.class)))
    })
    @GetMapping("/filter")
    public ResponseEntity<?> filterChangeEvents(@RequestParam String field, @RequestParam(defaultValue = "EQUALS") String operator, @RequestParam String value) {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + field, operator, value)
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    ChangeEvent.ENTITY_NAME,
                    String.valueOf(ChangeEvent.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("Execution error filtering change events", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while filtering change events", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid filter params", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error filtering change events", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class ChangeEventResponse {
        @Schema(description = "Event id")
        private String eventId;
        @Schema(description = "Laureate technical id")
        private String laureateId;
        @Schema(description = "Event type (NEW, UPDATED)")
        private String eventType;
        @Schema(description = "Serialized payload")
        private String payload;
        @Schema(description = "Created at timestamp")
        private String createdAt;
    }
}
