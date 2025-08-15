package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.hnitem.version_1.HNItem;
import com.java_template.common.service.EntityService;
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

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/items")
@Tag(name = "HNItem")
public class HNItemController {

    private static final Logger logger = LoggerFactory.getLogger(HNItemController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HNItemController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create HNItem", description = "Persist an HNItem")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createHNItem(@RequestBody HNItemRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            HNItem item = new HNItem();
            item.setId(request.getId());
            item.setType(request.getType());
            if (request.getOriginalJson() != null) item.setOriginalJson(request.getOriginalJson().toString());
            item.setImportTimestamp(request.getImportTimestamp());
            item.setState(request.getState());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    item
            );

            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error creating HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get HNItem by technicalId", description = "Retrieve HNItem by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getHNItem(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode node = itemFuture.get();
            if (node == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error retrieving HNItem", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get all HNItems", description = "Retrieve all HNItems")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAllHNItems() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error retrieving HNItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search HNItems", description = "Filter HNItems by a simple field condition")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchHNItems(@RequestBody SearchRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getField() == null || request.getOperator() == null || request.getValue() == null) {
                throw new IllegalArgumentException("field, operator and value are required");
            }
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$." + request.getField(), request.getOperator(), request.getValue())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    HNItem.ENTITY_NAME,
                    String.valueOf(HNItem.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (Exception e) {
            logger.error("Unexpected error searching HNItems", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private ResponseEntity<String> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        }
        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        }
        logger.error("ExecutionException in HNItemController", ee);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
    }

    @Data
    @Schema(name = "HNItemRequest", description = "Request to create an HNItem")
    public static class HNItemRequest {
        @Schema(description = "HN item id from Hacker News API")
        private Long id;
        @Schema(description = "HN item type from Hacker News API")
        private String type;
        @Schema(description = "Original JSON item from HN API")
        private JsonNode originalJson;
        @Schema(description = "Import timestamp (ISO-8601)")
        private String importTimestamp;
        @Schema(description = "State of the item")
        private String state;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "technicalId generated by the system")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}
