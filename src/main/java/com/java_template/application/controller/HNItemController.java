package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/hnitems")
@Tag(name = "HNItem", description = "Endpoints for managing HNItem entities")
public class HNItemController {

    private static final Logger logger = LoggerFactory.getLogger(HNItemController.class);
    private final EntityService entityService;

    public HNItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create HNItem(s)", description = "Create one or more HNItem records via an ImportJob-like mechanism")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importHNItems(@RequestBody ImportHNItemsRequest request) {
        try {
            if (request == null || request.getPayload() == null) {
                throw new IllegalArgumentException("payload is required");
            }

            // We create an ImportJob entity and persist it via entityService to start orchestration
            com.java_template.application.entity.importjob.version_1.ImportJob job = new com.java_template.application.entity.importjob.version_1.ImportJob();
            job.setPayload(request.getPayload());
            job.setSource(request.getSource());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    com.java_template.application.entity.importjob.version_1.ImportJob.ENTITY_NAME,
                    String.valueOf(com.java_template.application.entity.importjob.version_1.ImportJob.ENTITY_VERSION),
                    job
            );
            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("ExecutionException while importing HNItems", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while importing HNItems", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while importing HNItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get HNItem by id", description = "Retrieve an HN item by its business id. By default returns rawJson; set includeMetadata=true to include importTimestamp and metadata")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Object.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/byId/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getHNItemById(
            @Parameter(name = "id", description = "HN item id (business id)") @PathVariable String id,
            @RequestParam(value = "includeMetadata", required = false, defaultValue = "false") boolean includeMetadata
    ) {
        try {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            // Use entityService.getItemsByCondition to query by business id field (long) using JSON path
            SearchConditionRequest cond = SearchConditionRequest.group("AND",
                    Condition.of("$.id", "EQUALS", id)
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    cond,
                    true
            );
            ArrayNode arr = itemsFuture.get();
            if (arr == null || arr.size() == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("HNItem not found");
            }
            ObjectNode node = (ObjectNode) arr.get(0);
            if (!includeMetadata) {
                // Return rawJson field only if present
                if (node.has("rawJson")) {
                    return ResponseEntity.ok(node.get("rawJson"));
                }
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request: {}", iae.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("ExecutionException while fetching HNItem", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching HNItem", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while fetching HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List HNItems", description = "Retrieve all HNItem records")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Object.class))))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listHNItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("ExecutionException while listing HNItems", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing HNItems", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while listing HNItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class ImportHNItemsRequest {
        @Schema(description = "Payload to ingest; single HN item or array of items")
        private Object payload;
        @Schema(description = "Optional source/origin information")
        private String source;
    }

    @Data
    static class TechnicalIdResponse {
        @Schema(description = "Technical id of the created orchestration entity")
        private String technicalId;
    }
}
