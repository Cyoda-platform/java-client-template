package com.java_template.application.controller.datafeed.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.datafeed.version_1.DataFeed;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Controller for DataFeed entity operations.
 * This controller is a thin proxy to the EntityService. All business logic lives in workflows.
 */
@RestController
@RequestMapping(path = "/data-feeds")
@Tag(name = "DataFeed", description = "DataFeed entity operations (thin proxy to EntityService)")
public class DataFeedController {

    private static final Logger logger = LoggerFactory.getLogger(DataFeedController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public DataFeedController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Register DataFeed", description = "Register a new DataFeed. Returns technicalId of created resource.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<?> registerDataFeed(
            @RequestBody(description = "DataFeed registration request", required = true,
                    content = @Content(schema = @Schema(implementation = DataFeedRegisterRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody DataFeedRegisterRequest request) {
        try {
            // map request to generic ObjectNode to avoid enforcing entity validation here
            ObjectNode payload = objectMapper.valueToTree(request);
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    DataFeed.ENTITY_NAME,
                    String.valueOf(DataFeed.ENTITY_VERSION),
                    payload
            );
            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument during registerDataFeed", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in registerDataFeed", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while registering DataFeed", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in registerDataFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Get DataFeed by technicalId", description = "Retrieve a DataFeed by its technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content =
            @Content(schema = @Schema(implementation = DataFeedResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getDataFeedById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    DataFeed.ENTITY_NAME,
                    String.valueOf(DataFeed.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            DataFeedResponse resp = objectMapper.treeToValue(node, DataFeedResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId in getDataFeedById", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getDataFeedById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving DataFeed", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getDataFeedById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "List all DataFeeds", description = "Retrieve all DataFeed entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = DataFeedResponse.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<?> listDataFeeds() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    DataFeed.ENTITY_NAME,
                    String.valueOf(DataFeed.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            List<DataFeedResponse> list = new ArrayList<>();
            for (JsonNode node : array) {
                DataFeedResponse resp = objectMapper.treeToValue(node, DataFeedResponse.class);
                list.add(resp);
            }
            return ResponseEntity.ok(list);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in listDataFeeds", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing DataFeeds", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in listDataFeeds", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Search DataFeeds by condition", description = "Search DataFeed entities using a SearchConditionRequest")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content =
            @Content(array = @ArraySchema(schema = @Schema(implementation = DataFeedResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchDataFeeds(
            @RequestBody(description = "Search condition", required = true,
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                    DataFeed.ENTITY_NAME,
                    String.valueOf(DataFeed.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredFuture.get();
            List<DataFeedResponse> list = new ArrayList<>();
            for (JsonNode node : array) {
                DataFeedResponse resp = objectMapper.treeToValue(node, DataFeedResponse.class);
                list.add(resp);
            }
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid searchDataFeeds request", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchDataFeeds", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching DataFeeds", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in searchDataFeeds", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Update DataFeed", description = "Update an existing DataFeed by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateDataFeed(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @RequestBody(description = "DataFeed update request", required = true,
                    content = @Content(schema = @Schema(implementation = DataFeedUpdateRequest.class)))
            @org.springframework.web.bind.annotation.RequestBody DataFeedUpdateRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            ObjectNode payload = objectMapper.valueToTree(request);
            CompletableFuture<UUID> updateFuture = entityService.updateItem(
                    DataFeed.ENTITY_NAME,
                    String.valueOf(DataFeed.ENTITY_VERSION),
                    id,
                    payload
            );
            UUID updated = updateFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(updated.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid argument in updateDataFeed", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateDataFeed", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating DataFeed", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in updateDataFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete DataFeed", description = "Delete a DataFeed by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteDataFeed(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedFuture = entityService.deleteItem(
                    DataFeed.ENTITY_NAME,
                    String.valueOf(DataFeed.ENTITY_VERSION),
                    id
            );
            UUID deleted = deletedFuture.get();
            return ResponseEntity.ok(new TechnicalIdResponse(deleted.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId in deleteDataFeed", iae);
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteDataFeed", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause != null ? cause.getMessage() : ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting DataFeed", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in deleteDataFeed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // --- DTOs as static classes ---

    @Data
    @Schema(name = "DataFeedRegisterRequest", description = "Request to register a DataFeed")
    public static class DataFeedRegisterRequest {
        @Schema(description = "Source CSV URL", required = true, example = "https://raw.githubusercontent.com/.../london_houses.csv")
        private String url;
        @Schema(description = "Friendly name", required = true, example = "London Houses CSV")
        private String name;
        @Schema(description = "Schedule specification (optional)", required = false, example = "daily")
        private String scheduleSpec;
    }

    @Data
    @Schema(name = "DataFeedUpdateRequest", description = "Request to update a DataFeed (partial updates allowed)")
    public static class DataFeedUpdateRequest {
        @Schema(description = "Friendly name", example = "London Houses CSV")
        private String name;
        @Schema(description = "Source CSV URL", example = "https://raw.githubusercontent.com/.../london_houses.csv")
        private String url;
        @Schema(description = "Status", example = "VALIDATED")
        private String status;
        @Schema(description = "Last fetched timestamp (ISO)", example = "2025-08-01T12:00:00Z")
        private String lastFetchedAt;
        @Schema(description = "Last checksum", example = "abc123")
        private String lastChecksum;
        @Schema(description = "Record count", example = "1000")
        private Integer recordCount;
        @Schema(description = "Schema preview map", example = "{\"price\":\"numeric\",\"bedrooms\":\"integer\"}")
        private java.util.Map<String, String> schemaPreview;
    }

    @Data
    @Schema(name = "DataFeedResponse", description = "DataFeed response payload")
    public static class DataFeedResponse {
        @Schema(description = "Technical id", example = "df_12345")
        private String id;
        @Schema(description = "Source CSV URL", example = "https://raw.githubusercontent.com/.../london_houses.csv")
        private String url;
        @Schema(description = "Friendly name", example = "London Houses CSV")
        private String name;
        @Schema(description = "Last fetched timestamp (ISO)", example = "2025-08-01T12:00:00Z")
        private String lastFetchedAt;
        @Schema(description = "Record count", example = "1000")
        private Integer recordCount;
        @Schema(description = "Schema preview", example = "{\"price\":\"numeric\",\"bedrooms\":\"integer\"}")
        private java.util.Map<String, String> schemaPreview;
        @Schema(description = "Status", example = "VALIDATED")
        private String status;
        @Schema(description = "Created at timestamp (ISO)", example = "2025-08-01T11:50:00Z")
        private String createdAt;
        @Schema(description = "Updated at timestamp (ISO)", example = "2025-08-01T12:00:00Z")
        private String updatedAt;
        @Schema(description = "Last checksum", example = "abc123")
        private String lastChecksum;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing a technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical id", example = "a3e1f6a2-7c3b-4a6f-9d2c-1e1f1b2a3c4d")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}