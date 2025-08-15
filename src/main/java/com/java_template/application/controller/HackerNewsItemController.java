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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/items")
@Tag(name = "HackerNewsItem Controller", description = "Endpoints to store and retrieve Hacker News items")
public class HackerNewsItemController {
    private static final Logger logger = LoggerFactory.getLogger(HackerNewsItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HackerNewsItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Save Hacker News item", description = "Accepts Hacker News JSON, validates basic shape, and stores it.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Created", content = @Content(schema = @Schema(implementation = SaveResponse.class))),
            @ApiResponse(responseCode = "200", description = "OK (updated)", content = @Content(schema = @Schema(implementation = SaveResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveItem(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Hacker News item JSON") @RequestBody JsonNode body) {
        try {
            logger.info("Received save item request: {}", body);
            // Basic JSON validity
            if (body == null || body.isNull()) {
                return badRequest("validation_error", "Request body must be valid JSON");
            }

            // Validate id
            JsonNode idNode = body.get("id");
            if (idNode == null || idNode.isNull()) {
                return badRequest("validation_error", "Field 'id' is required");
            }

            String idAsText;
            if (idNode.isIntegralNumber()) {
                long v = idNode.longValue();
                if (v < 0) return badRequest("validation_error", "Field 'id' must be >= 0");
                idAsText = Long.toString(v);
            } else if (idNode.isTextual()) {
                String txt = idNode.asText();
                try {
                    long v = Long.parseLong(txt);
                    if (v < 0) return badRequest("validation_error", "Field 'id' must be >= 0");
                    idAsText = Long.toString(v);
                } catch (NumberFormatException nfe) {
                    return badRequest("validation_error", "Field 'id' must be an integer");
                }
            } else {
                return badRequest("validation_error", "Field 'id' must be an integer");
            }

            // Validate type
            JsonNode typeNode = body.get("type");
            if (typeNode == null || typeNode.isNull() || !typeNode.isTextual() || typeNode.asText().isBlank()) {
                return badRequest("validation_error", "Field 'type' is required and must be a non-empty string");
            }

            String type = typeNode.asText();

            // Detect existence by searching on stored field $.id
            ArrayNode found = entityService.getItemsByCondition(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.id", "EQUALS", idAsText)),
                    true
            ).get();

            boolean exists = found != null && found.size() > 0;

            // Build entity to store - controller must not enrich importTimestamp, workflows will do that
            HackerNewsItem entity = new HackerNewsItem();
            entity.setId(idAsText);
            entity.setType(type);
            entity.setOriginalJson(objectMapper.writeValueAsString(body));

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    entity
            );
            java.util.UUID technicalId = idFuture.get();

            SaveResponse resp = new SaveResponse();
            resp.setTechnicalId(technicalId);
            resp.setItemId(idAsText);

            if (exists) {
                return ResponseEntity.ok(resp);
            } else {
                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(URI.create(String.format("/items/%s", idAsText)));
                return ResponseEntity.status(201).headers(headers).body(resp);
            }

        } catch (IllegalArgumentException iae) {
            return badRequest("validation_error", iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(errorBody("not_found", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return badRequest("validation_error", cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body(errorBody("server_error", ee.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(500).body(errorBody("server_error", ex.getMessage()));
        }
    }

    @Operation(summary = "Retrieve Hacker News item", description = "Retrieve stored Hacker News item by HN id (the 'id' field in the HN JSON)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getItem(@Parameter(name = "id", description = "Hacker News item id") @PathVariable String id) {
        try {
            logger.info("Retrieve item by HN id: {}", id);
            if (id == null || id.isBlank()) {
                return badRequest("validation_error", "Path parameter id is required");
            }

            ArrayNode found = entityService.getItemsByCondition(
                    HackerNewsItem.ENTITY_NAME,
                    String.valueOf(HackerNewsItem.ENTITY_VERSION),
                    SearchConditionRequest.group("AND", Condition.of("$.id", "EQUALS", id)),
                    true
            ).get();

            if (found == null || found.size() == 0) {
                return ResponseEntity.status(404).body(errorBody("not_found", "No item with requested id"));
            }

            // Return the first matched stored JSON as-is
            JsonNode stored = found.get(0);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(stored);

        } catch (IllegalArgumentException iae) {
            return badRequest("validation_error", iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(errorBody("not_found", cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return badRequest("validation_error", cause.getMessage());
            } else {
                logger.error("Execution error", ee);
                return ResponseEntity.status(500).body(errorBody("server_error", ee.getMessage()));
            }
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(500).body(errorBody("server_error", ex.getMessage()));
        }
    }

    private ResponseEntity<Object> badRequest(String error, String message) {
        return ResponseEntity.badRequest().body(errorBody(error, message));
    }

    private ObjectNode errorBody(String error, String message) {
        ObjectNode obj = objectMapper.createObjectNode();
        obj.put("error", error == null ? "server_error" : error);
        obj.put("message", message == null ? "" : message);
        return obj;
    }

    @Data
    static class SaveResponse {
        @Schema(description = "Technical UUID assigned to the stored entity")
        private java.util.UUID technicalId;

        @Schema(description = "Original Hacker News item id")
        private String itemId;
    }
}
