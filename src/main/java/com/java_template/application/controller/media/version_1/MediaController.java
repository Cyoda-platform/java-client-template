package com.java_template.application.controller.media.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.media.version_1.Media;
import com.java_template.common.service.EntityService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/media/v1")
@Tag(name = "Media", description = "Media entity proxy endpoints (version 1)")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final EntityService entityService;

    public MediaController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Media", description = "Persist a new Media entity and trigger processing workflows")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createMedia(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Media create payload", required = true,
            content = @Content(schema = @Schema(implementation = CreateMediaRequest.class)))
                                         @RequestBody CreateMediaRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            if (request.getFilename() == null || request.getFilename().isBlank())
                throw new IllegalArgumentException("filename is required");
            if (request.getMime() == null || request.getMime().isBlank())
                throw new IllegalArgumentException("mime is required");

            Media data = new Media();
            data.setOwner_id(request.getOwnerId());
            data.setFilename(request.getFilename());
            data.setMime(request.getMime());
            // other fields intentionally not set here; workflows will handle processing

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Media.ENTITY_NAME,
                    String.valueOf(Media.ENTITY_VERSION),
                    data
            );
            UUID technicalId = idFuture.get();
            return ResponseEntity.ok(new CreateResponse(technicalId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createMedia request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in createMedia", ee);
                return ResponseEntity.status(500).body(new ErrorResponse(cause == null ? ee.getMessage() : cause.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createMedia", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Create multiple Media items", description = "Persist multiple Media entities in batch")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BatchCreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createMediaBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Batch create payload", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreateMediaRequest.class))))
                                              @RequestBody List<CreateMediaRequest> requests) {
        try {
            if (requests == null || requests.isEmpty())
                throw new IllegalArgumentException("Request list must not be empty");

            List<Media> medias = requests.stream().map(r -> {
                Media m = new Media();
                m.setOwner_id(r.getOwnerId());
                m.setFilename(r.getFilename());
                m.setMime(r.getMime());
                return m;
            }).collect(Collectors.toList());

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Media.ENTITY_NAME,
                    String.valueOf(Media.ENTITY_VERSION),
                    medias
            );
            List<UUID> ids = idsFuture.get();
            List<String> technicalIds = ids.stream().map(UUID::toString).collect(Collectors.toList());
            return ResponseEntity.ok(new BatchCreateResponse(technicalIds));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid createMediaBatch request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in createMediaBatch", ee);
                return ResponseEntity.status(500).body(new ErrorResponse(cause == null ? ee.getMessage() : cause.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in createMediaBatch", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Get Media by technicalId", description = "Retrieve a Media entity by its technical UUID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Media.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getMediaById(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                          @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Media.ENTITY_NAME,
                    String.valueOf(Media.ENTITY_VERSION),
                    id
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId in getMediaById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in getMediaById", ee);
                return ResponseEntity.status(500).body(new ErrorResponse(cause == null ? ee.getMessage() : cause.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getMediaById", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Get all Media items", description = "Retrieve all Media entities")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Media.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllMedia() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Media.ENTITY_NAME,
                    String.valueOf(Media.ENTITY_VERSION)
            );
            ArrayNode items = itemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in getAllMedia", ee);
                return ResponseEntity.status(500).body(new ErrorResponse(cause == null ? ee.getMessage() : cause.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in getAllMedia", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Search Media by condition", description = "Retrieve Media entities matching a search condition (in-memory filtering)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Media.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchMedia(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
                                         @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) throw new IllegalArgumentException("Search condition is required");
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Media.ENTITY_NAME,
                    String.valueOf(Media.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode items = filteredItemsFuture.get();
            return ResponseEntity.ok(items);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid searchMedia request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in searchMedia", ee);
                return ResponseEntity.status(500).body(new ErrorResponse(cause == null ? ee.getMessage() : cause.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in searchMedia", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Update Media", description = "Update an existing Media entity")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateMedia(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                         @PathVariable String technicalId,
                                         @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Media update payload", required = true,
                                                 content = @Content(schema = @Schema(implementation = UpdateMediaRequest.class)))
                                         @RequestBody UpdateMediaRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            UUID id = UUID.fromString(technicalId);

            Media data = new Media();
            // Controller must not implement business logic; map provided fields only
            data.setOwner_id(request.getOwnerId());
            data.setFilename(request.getFilename());
            data.setMime(request.getMime());
            data.setCdn_ref(request.getCdnRef());
            data.setStatus(request.getStatus());
            data.setCreated_at(request.getCreatedAt());
            data.setMedia_id(request.getMediaId());
            data.setVersions(request.getVersions());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Media.ENTITY_NAME,
                    String.valueOf(Media.ENTITY_VERSION),
                    id,
                    data
            );
            UUID updatedId = updatedIdFuture.get();
            return ResponseEntity.ok(new CreateResponse(updatedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid updateMedia request: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in updateMedia", ee);
                return ResponseEntity.status(500).body(new ErrorResponse(cause == null ? ee.getMessage() : cause.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in updateMedia", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    @Operation(summary = "Delete Media", description = "Delete a Media entity by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CreateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteMedia(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                         @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Media.ENTITY_NAME,
                    String.valueOf(Media.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            return ResponseEntity.ok(new CreateResponse(deletedId.toString()));
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId in deleteMedia: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(new ErrorResponse(iae.getMessage()));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(new ErrorResponse(cause.getMessage()));
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(new ErrorResponse(cause.getMessage()));
            } else {
                logger.error("ExecutionException in deleteMedia", ee);
                return ResponseEntity.status(500).body(new ErrorResponse(cause == null ? ee.getMessage() : cause.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in deleteMedia", e);
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }

    // DTOs

    @Data
    @Schema(name = "CreateMediaRequest", description = "Payload to create a Media")
    public static class CreateMediaRequest {
        @Schema(description = "Owner ID of the media", example = "user-123")
        private String ownerId;
        @Schema(description = "Filename of the media", example = "image.jpg")
        private String filename;
        @Schema(description = "MIME type", example = "image/jpeg")
        private String mime;
    }

    @Data
    @Schema(name = "UpdateMediaRequest", description = "Payload to update a Media")
    public static class UpdateMediaRequest {
        @Schema(description = "Serialized media id", example = "media-123")
        private String mediaId;
        @Schema(description = "Owner ID of the media", example = "user-123")
        private String ownerId;
        @Schema(description = "Filename of the media", example = "image.jpg")
        private String filename;
        @Schema(description = "MIME type", example = "image/jpeg")
        private String mime;
        @Schema(description = "CDN reference", example = "https://cdn.example.com/abc")
        private String cdnRef;
        @Schema(description = "ISO created at timestamp", example = "2025-08-01T12:00:00Z")
        private String createdAt;
        @Schema(description = "Status of media", example = "uploaded")
        private String status;
        @Schema(description = "Versions metadata")
        private List<Object> versions;
    }

    @Data
    @Schema(name = "CreateResponse", description = "Response containing the technical id")
    public static class CreateResponse {
        @Schema(description = "Technical ID assigned to the entity", example = "6f1e2a9e-3d7b-4a2b-a9f9-0a1b2c3d4e5f")
        private String technicalId;

        public CreateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BatchCreateResponse", description = "Response containing multiple technical ids")
    public static class BatchCreateResponse {
        @Schema(description = "List of technical IDs", example = "[\"id1\",\"id2\"]")
        private List<String> technicalIds;

        public BatchCreateResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "ErrorResponse", description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }
    }
}