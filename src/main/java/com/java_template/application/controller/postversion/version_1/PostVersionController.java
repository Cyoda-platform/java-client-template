package com.java_template.application.controller.postversion.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.postversion.version_1.PostVersion;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.ExampleObject;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/post-versions")
@Tag(name = "PostVersion", description = "PostVersion entity proxy API (v1)")
public class PostVersionController {

    private static final Logger logger = LoggerFactory.getLogger(PostVersionController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PostVersionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Create PostVersion", description = "Persist a new PostVersion entity and return a technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPostVersion(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "PostVersion creation payload", required = true,
                    content = @Content(schema = @Schema(implementation = PostVersionRequest.class)))
            @RequestBody PostVersionRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic request format validation (no business logic)
            if (request.getPost_id() == null || request.getPost_id().isBlank()) {
                throw new IllegalArgumentException("post_id is required");
            }
            // Map request to entity. Business rules (like generating version_id/created_at) are handled in workflows.
            PostVersion entity = new PostVersion();
            entity.setPost_id(request.getPost_id());
            entity.setAuthor_id(request.getAuthor_id());
            entity.setContent_rich(request.getContent_rich());
            // other fields intentionally left null for workflow processors to populate

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    PostVersion.ENTITY_NAME,
                    PostVersion.ENTITY_VERSION,
                    entity
            );
            UUID entityId = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(entityId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to create PostVersion: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when creating PostVersion", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating PostVersion", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while creating PostVersion", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Get PostVersion by technicalId", description = "Retrieve a PostVersion entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PostVersionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPostVersion(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            if (dataPayload == null || dataPayload.getData() == null) {
                return ResponseEntity.status(404).body("PostVersion not found");
            }
            JsonNode dataNode = dataPayload.getData();
            PostVersionResponse response = objectMapper.treeToValue(dataNode, PostVersionResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to get PostVersion: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when retrieving PostVersion", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving PostVersion", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while retrieving PostVersion", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "List PostVersions", description = "Retrieve list of PostVersion entities. Optional filter by post_id query parameter.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = PostVersionResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> listPostVersions(
            @Parameter(name = "postId", description = "Optional filter by parent post id")
            @RequestParam(value = "postId", required = false) String postId
    ) {
        try {
            List<DataPayload> dataPayloads;
            if (postId != null && !postId.isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.post_id", "EQUALS", postId)
                );
                CompletableFuture<List<DataPayload>> future = entityService.getItemsByCondition(
                        PostVersion.ENTITY_NAME, PostVersion.ENTITY_VERSION, condition, true
                );
                dataPayloads = future.get();
            } else {
                CompletableFuture<List<DataPayload>> future = entityService.getItems(
                        PostVersion.ENTITY_NAME, PostVersion.ENTITY_VERSION, null, null, null
                );
                dataPayloads = future.get();
            }

            List<PostVersionResponse> responses = new ArrayList<>();
            if (dataPayloads != null) {
                for (DataPayload payload : dataPayloads) {
                    if (payload != null && payload.getData() != null) {
                        PostVersionResponse resp = objectMapper.treeToValue(payload.getData(), PostVersionResponse.class);
                        responses.add(resp);
                    }
                }
            }
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to list PostVersions: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when listing PostVersions", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing PostVersions", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while listing PostVersions", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Update PostVersion", description = "Update an existing PostVersion entity by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePostVersion(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "PostVersion update payload", required = true,
                    content = @Content(schema = @Schema(implementation = PostVersionRequest.class)))
            @RequestBody PostVersionRequest request
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            PostVersion entity = new PostVersion();
            entity.setPost_id(request.getPost_id());
            entity.setAuthor_id(request.getAuthor_id());
            entity.setContent_rich(request.getContent_rich());
            // Note: controller must not implement business logic like updating timestamps/version_id

            CompletableFuture<UUID> updFuture = entityService.updateItem(UUID.fromString(technicalId), entity);
            UUID updatedId = updFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to update PostVersion: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when updating PostVersion", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating PostVersion", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while updating PostVersion", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @Operation(summary = "Delete PostVersion", description = "Delete a PostVersion entity by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePostVersion(
            @Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
            @PathVariable("technicalId") String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<UUID> delFuture = entityService.deleteItem(UUID.fromString(technicalId));
            UUID deletedId = delFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request to delete PostVersion: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(404).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException when deleting PostVersion", ee);
                return ResponseEntity.status(500).body(ee.getMessage());
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting PostVersion", ie);
            return ResponseEntity.status(500).body(ie.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error while deleting PostVersion", e);
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    // Request and Response DTOs

    @Data
    @Schema(name = "PostVersionRequest", description = "Payload to create/update a PostVersion")
    public static class PostVersionRequest {
        @Schema(description = "Reference to parent Post", example = "post-123")
        private String post_id;

        @Schema(description = "Author reference", example = "user-123")
        private String author_id;

        @Schema(description = "Rich content (editor delta or HTML)", example = "<p>Hello</p>")
        private String content_rich;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing created entity technicalId")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the persisted entity", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;
    }

    @Data
    @Schema(name = "PostVersionResponse", description = "Full PostVersion representation")
    public static class PostVersionResponse {
        @Schema(description = "Version identifier", example = "pv-456")
        private String version_id;

        @Schema(description = "Parent Post id", example = "post-123")
        private String post_id;

        @Schema(description = "Author id", example = "user-123")
        private String author_id;

        @Schema(description = "Creation timestamp (ISO)", example = "2025-08-01T12:00:00Z")
        private String created_at;

        @Schema(description = "Change summary", example = "Added introduction")
        private String change_summary;

        @Schema(description = "Rich content", example = "<p>...</p>")
        private String content_rich;

        @Schema(description = "Normalized plaintext", example = "Hello world")
        private String normalized_text;

        @Schema(description = "Embeddings reference", example = "emb-ref-001")
        private String embeddings_ref;

        @Schema(description = "Chunks metadata")
        private List<ChunkMetaResponse> chunks_meta;

        @Data
        @Schema(name = "ChunkMeta", description = "Metadata for a content chunk")
        public static class ChunkMetaResponse {
            @Schema(description = "Chunk reference id", example = "chunk-1")
            private String chunk_ref;

            @Schema(description = "Chunk plaintext", example = "Hello world")
            private String text;
        }
    }
}