package com.java_template.application.controller.datasource.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/data-source")
@Tag(name = "DataSource", description = "DataSource entity proxy controller (version 1)")
public class DataSourceController {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public DataSourceController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get DataSource by technicalId", description = "Retrieve a DataSource entity by its technical UUID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DataSourceResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDataSourceById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null || node.isNull()) {
                return ResponseEntity.notFound().build();
            }
            // Convert to entity then to response DTO
            DataSource ds = objectMapper.treeToValue(node, DataSource.class);
            DataSourceResponse resp = mapEntityToResponse(ds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in getDataSourceById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getDataSourceById", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getDataSourceById", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "List DataSource items", description = "Retrieve all DataSource entities")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = DataSourceResponse.class)))),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listDataSources() {
        try {
            CompletableFuture<List<DataPayload>> itemsFuture = entityService.getItems(
                    DataSource.ENTITY_NAME,
                    DataSource.ENTITY_VERSION,
                    null, null, null
            );
            List<DataPayload> dataPayloads = itemsFuture.get();
            List<DataSourceResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload == null) continue;
                    try {
                        DataSource ds = objectMapper.treeToValue(payload.getData(), DataSource.class);
                        responses.add(mapEntityToResponse(ds));
                    } catch (Exception ex) {
                        logger.warn("Failed to map DataPayload to DataSourceResponse, skipping item", ex);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            logger.error("ExecutionException in listDataSources", ee);
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error in listDataSources", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Update DataSource", description = "Update an existing DataSource entity (admin operation)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "DataSource update payload")
    @PutMapping(path = "/{technicalId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateDataSource(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId,
            @RequestBody DataSourceRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            // Map request to entity
            DataSource entity = new DataSource();
            // Keep id from path for technical id consistency
            entity.setId(request.getId() != null && !request.getId().isBlank() ? request.getId() : technicalId);
            entity.setUrl(request.getUrl());
            entity.setLastFetchedAt(request.getLastFetchedAt());
            entity.setSampleHash(request.getSampleHash());
            entity.setSchema(request.getSchema());
            entity.setValidationStatus(request.getValidationStatus());

            CompletableFuture<java.util.UUID> updatedIdFuture = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID updatedId = updatedIdFuture.get();
            UpdateResponse resp = new UpdateResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in updateDataSource: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updateDataSource", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updateDataSource", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    @Operation(summary = "Delete DataSource", description = "Delete a DataSource entity by technicalId (admin operation)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping(path = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deleteDataSource(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable("technicalId") String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = deletedIdFuture.get();
            DeleteResponse resp = new DeleteResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Bad request in deleteDataSource: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deleteDataSource", ee);
                return ResponseEntity.status(500).body("Internal server error");
            }
        } catch (Exception e) {
            logger.error("Unexpected error in deleteDataSource", e);
            return ResponseEntity.status(500).body("Internal server error");
        }
    }

    // Mapping helper
    private DataSourceResponse mapEntityToResponse(DataSource ds) {
        DataSourceResponse r = new DataSourceResponse();
        if (ds == null) return r;
        r.setId(ds.getId());
        r.setUrl(ds.getUrl());
        r.setLastFetchedAt(ds.getLastFetchedAt());
        r.setSchema(ds.getSchema());
        r.setValidationStatus(ds.getValidationStatus());
        r.setSampleHash(ds.getSampleHash());
        return r;
    }

    // Static DTO classes

    @Data
    @Schema(name = "DataSourceResponse", description = "Response payload for DataSource")
    public static class DataSourceResponse {
        @Schema(description = "Internal id of this data source record")
        @JsonProperty("id")
        private String id;

        @Schema(description = "Source URL to fetch CSV")
        @JsonProperty("url")
        private String url;

        @Schema(description = "Timestamp of last fetch")
        @JsonProperty("last_fetched_at")
        private String lastFetchedAt;

        @Schema(description = "Serialized schema/columns")
        @JsonProperty("schema")
        private String schema;

        @Schema(description = "Validation status (VALID/INVALID)")
        @JsonProperty("validation_status")
        private String validationStatus;

        @Schema(description = "Content fingerprint")
        @JsonProperty("sample_hash")
        private String sampleHash;
    }

    @Data
    @Schema(name = "DataSourceRequest", description = "Request payload for updating DataSource")
    public static class DataSourceRequest {
        @Schema(description = "Internal id of this data source record (optional, path id takes precedence)")
        @JsonProperty("id")
        private String id;

        @Schema(description = "Source URL to fetch CSV")
        @JsonProperty("url")
        private String url;

        @Schema(description = "Timestamp of last fetch")
        @JsonProperty("last_fetched_at")
        private String lastFetchedAt;

        @Schema(description = "Serialized schema/columns")
        @JsonProperty("schema")
        private String schema;

        @Schema(description = "Validation status (VALID/INVALID)")
        @JsonProperty("validation_status")
        private String validationStatus;

        @Schema(description = "Content fingerprint")
        @JsonProperty("sample_hash")
        private String sampleHash;
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Update operation response containing technicalId")
    public static class UpdateResponse {
        @Schema(description = "Technical id of the updated entity")
        @JsonProperty("technicalId")
        private String technicalId;
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Delete operation response containing technicalId")
    public static class DeleteResponse {
        @Schema(description = "Technical id of the deleted entity")
        @JsonProperty("technicalId")
        private String technicalId;
    }
}