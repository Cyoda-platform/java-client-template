package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/hn-items")
@Tag(name = "HackerNewsItem")
public class HackerNewsItemController {
    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public HackerNewsItemController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get HackerNewsItem by Hacker News id", description = "Retrieve a stored Hacker News item by its Hacker News id (numeric id inside original JSON). Returns the original JSON parsed plus metadata.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = HackerNewsItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(path = "/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getByHnId(@Parameter(name = "id", description = "Hacker News numeric id") @PathVariable Long id) {
        try {
            if (id == null) throw new IllegalArgumentException("id is required");

            SearchConditionRequest condition = SearchConditionRequest.group("AND", Condition.of("$.id", "EQUALS", String.valueOf(id)));
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode results = itemsFuture.get();
            if (results == null || results.size() == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("HackerNewsItem not found");
            }
            ObjectNode node = (ObjectNode) results.get(0);
            HackerNewsItemResponse resp = new HackerNewsItemResponse();
            // originalJson is stored as verbatim string; parse it back to JSON
            if (node.hasNonNull("originalJson")) {
                String originalJsonStr = node.get("originalJson").asText();
                try {
                    JsonNode parsed = objectMapper.readTree(originalJsonStr);
                    resp.setOriginalJson(parsed);
                } catch (Exception e) {
                    // if parsing fails, return the raw string as text node
                    resp.setOriginalJson(objectMapper.createObjectNode().put("originalJsonString", originalJsonStr));
                }
            }
            if (node.hasNonNull("id")) resp.setId(node.get("id").asLong());
            if (node.hasNonNull("type")) resp.setType(node.get("type").asText());
            if (node.hasNonNull("importTimestamp")) resp.setImportTimestamp(node.get("importTimestamp").asText());
            if (node.hasNonNull("state")) resp.setState(node.get("state").asText());
            if (node.hasNonNull("validationErrors")) resp.setValidationErrors(node.get("validationErrors").asText());
            if (node.hasNonNull("createdAt")) resp.setCreatedAt(node.get("createdAt").asText());
            if (node.hasNonNull("source")) resp.setSource(node.get("source").asText());
            if (node.hasNonNull("tags")) resp.setTags(node.get("tags").toString());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching HackerNewsItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching HackerNewsItem", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while fetching HackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get HackerNewsItem by technicalId", description = "Retrieve a HackerNewsItem by datastore technical id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = HackerNewsItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(path = "/technical/{technicalId}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getByTechnicalId(@Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isNull()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("HackerNewsItem not found");
            }
            HackerNewsItemResponse resp = new HackerNewsItemResponse();
            if (node.hasNonNull("originalJson")) {
                String originalJsonStr = node.get("originalJson").asText();
                try {
                    JsonNode parsed = objectMapper.readTree(originalJsonStr);
                    resp.setOriginalJson(parsed);
                } catch (Exception e) {
                    resp.setOriginalJson(objectMapper.createObjectNode().put("originalJsonString", originalJsonStr));
                }
            }
            if (node.hasNonNull("id")) resp.setId(node.get("id").asLong());
            if (node.hasNonNull("type")) resp.setType(node.get("type").asText());
            if (node.hasNonNull("importTimestamp")) resp.setImportTimestamp(node.get("importTimestamp").asText());
            if (node.hasNonNull("state")) resp.setState(node.get("state").asText());
            if (node.hasNonNull("validationErrors")) resp.setValidationErrors(node.get("validationErrors").asText());
            if (node.hasNonNull("createdAt")) resp.setCreatedAt(node.get("createdAt").asText());
            if (node.hasNonNull("source")) resp.setSource(node.get("source").asText());
            if (node.hasNonNull("tags")) resp.setTags(node.get("tags").toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while fetching HackerNewsItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while fetching HackerNewsItem", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while fetching HackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Create HackerNewsItem (testing/admin)", description = "Create a HackerNewsItem. Note: in normal operation items are created by ImportJob processing. This endpoint is a simple proxy to entityService.addItem.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @RequestMapping(method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createHnItem(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "HackerNewsItem create request") @RequestBody CreateHnItemRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("request body is required");
            if (request.getOriginalJson() == null || request.getOriginalJson().isNull()) throw new IllegalArgumentException("originalJson is required");

            HackerNewsItem item = new HackerNewsItem();
            String originalStr = objectMapper.writeValueAsString(request.getOriginalJson());
            item.setOriginalJson(originalStr);
            item.setCreatedAt(Instant.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    item
            );
            UUID technicalId = idFuture.get();
            CreateResponse resp = new CreateResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            } else {
                logger.error("ExecutionException while creating HackerNewsItem", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating HackerNewsItem", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating HackerNewsItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    static class CreateHnItemRequest {
        @Schema(description = "Original Firebase-format Hacker News JSON object", required = true)
        private JsonNode originalJson;
    }

    @Data
    static class CreateResponse {
        @Schema(description = "Technical id of the created entity")
        private String technicalId;
    }

    @Data
    static class HackerNewsItemResponse {
        @Schema(description = "Original Firebase-format Hacker News JSON object")
        private JsonNode originalJson;
        @Schema(description = "Hacker News id")
        private Long id;
        @Schema(description = "Hacker News type")
        private String type;
        @Schema(description = "Import timestamp (ISO-8601)")
        private String importTimestamp;
        @Schema(description = "Domain state (VALID or INVALID)")
        private String state;
        @Schema(description = "Validation errors if any")
        private String validationErrors;
        @Schema(description = "Created at timestamp")
        private String createdAt;
        @Schema(description = "Source/domain extracted from URL")
        private String source;
        @Schema(description = "Tags assigned during enrichment (JSON array as string)")
        private String tags;
    }
}
