package com.java_template.application.controller.postversion.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.postversion.version_1.PostVersion;
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
import jakarta.validation.Valid;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1/post-versions")
@Tag(name = "PostVersion", description = "Controller for PostVersion entity (version 1). Proxy to EntityService.")
public class PostVersionController {

    private static final Logger logger = LoggerFactory.getLogger(PostVersionController.class);

    private final EntityService entityService;

    public PostVersionController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create PostVersion", description = "Persist a new PostVersion entity. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createPostVersion(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Create PostVersion payload", required = true,
            content = @Content(schema = @Schema(implementation = CreatePostVersionRequest.class)))
                                               @Valid @RequestBody CreatePostVersionRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            // Basic format validation (no business logic)
            if (isBlank(request.getPostId()) || isBlank(request.getAuthorId())) {
                throw new IllegalArgumentException("postId and authorId are required");
            }

            PostVersion data = mapToEntity(request);

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    PostVersion.ENTITY_NAME,
                    String.valueOf(PostVersion.ENTITY_VERSION),
                    data
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPostVersion: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPostVersion", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating PostVersion", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createPostVersion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Create multiple PostVersions", description = "Persist multiple PostVersion entities. Returns list of technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TechnicalIdsResponse.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/batch")
    public ResponseEntity<?> createPostVersionsBatch(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Array of PostVersion payloads", required = true,
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreatePostVersionRequest.class))))
                                                    @Valid @RequestBody List<CreatePostVersionRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) {
                throw new IllegalArgumentException("Request list cannot be empty");
            }

            List<PostVersion> entities = new ArrayList<>();
            for (CreatePostVersionRequest r : requests) {
                if (r == null) continue;
                if (isBlank(r.getPostId()) || isBlank(r.getAuthorId())) {
                    throw new IllegalArgumentException("Each request must include postId and authorId");
                }
                entities.add(mapToEntity(r));
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    PostVersion.ENTITY_NAME,
                    String.valueOf(PostVersion.ENTITY_VERSION),
                    entities
            );

            List<UUID> ids = idsFuture.get();
            TechnicalIdsResponse resp = new TechnicalIdsResponse();
            List<String> sid = new ArrayList<>();
            for (UUID id : ids) sid.add(id.toString());
            resp.setTechnicalIds(sid);
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPostVersionsBatch: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in createPostVersionsBatch", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating PostVersions batch", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in createPostVersionsBatch", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get PostVersion by technicalId", description = "Retrieve a PostVersion by its technical UUID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ObjectNode.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getPostVersionById(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                                @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    PostVersion.ENTITY_NAME,
                    String.valueOf(PostVersion.ENTITY_VERSION),
                    id
            );

            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getPostVersionById: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPostVersionById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting PostVersion", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getPostVersionById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get all PostVersions", description = "Retrieve all PostVersion entities.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getAllPostVersions() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    PostVersion.ENTITY_NAME,
                    String.valueOf(PostVersion.ENTITY_VERSION)
            );
            ArrayNode arr = itemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getAllPostVersions", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting all PostVersions", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getAllPostVersions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Search PostVersions", description = "Retrieve PostVersion entities matching a search condition (in-memory).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/search")
    public ResponseEntity<?> searchPostVersions(@io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition", required = true,
            content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
                                               @Valid @RequestBody SearchConditionRequest condition) {
        try {
            if (condition == null) {
                throw new IllegalArgumentException("Search condition is required");
            }

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    PostVersion.ENTITY_NAME,
                    String.valueOf(PostVersion.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for searchPostVersions: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in searchPostVersions", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching PostVersions", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in searchPostVersions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Get PostVersions by postId", description = "Convenience endpoint to retrieve PostVersions for a given postId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectNode.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/by-post/{postId}")
    public ResponseEntity<?> getPostVersionsByPostId(@Parameter(name = "postId", description = "Post id to filter by", required = true)
                                                     @PathVariable("postId") String postId) {
        try {
            if (isBlank(postId)) {
                throw new IllegalArgumentException("postId is required");
            }

            SearchConditionRequest cond = SearchConditionRequest.group("AND",
                    Condition.of("$.postId", "EQUALS", postId)
            );

            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    PostVersion.ENTITY_NAME,
                    String.valueOf(PostVersion.ENTITY_VERSION),
                    cond,
                    true
            );

            ArrayNode arr = filteredItemsFuture.get();
            return ResponseEntity.ok(arr);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for getPostVersionsByPostId: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in getPostVersionsByPostId", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while getting PostVersions by postId", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in getPostVersionsByPostId", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Update PostVersion", description = "Update an existing PostVersion by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updatePostVersion(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                               @PathVariable("technicalId") String technicalId,
                                               @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Update PostVersion payload", required = true,
                                                       content = @Content(schema = @Schema(implementation = CreatePostVersionRequest.class)))
                                               @Valid @RequestBody CreatePostVersionRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }

            // Basic validation
            if (isBlank(request.getPostId()) || isBlank(request.getAuthorId())) {
                throw new IllegalArgumentException("postId and authorId are required");
            }

            PostVersion data = mapToEntity(request);

            CompletableFuture<UUID> updatedId = entityService.updateItem(
                    PostVersion.ENTITY_NAME,
                    String.valueOf(PostVersion.ENTITY_VERSION),
                    id,
                    data
            );

            UUID ret = updatedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(ret.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updatePostVersion: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in updatePostVersion", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating PostVersion", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in updatePostVersion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "Delete PostVersion", description = "Delete a PostVersion by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deletePostVersion(@Parameter(name = "technicalId", description = "Technical ID of the entity", required = true)
                                               @PathVariable("technicalId") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);

            CompletableFuture<UUID> deletedId = entityService.deleteItem(
                    PostVersion.ENTITY_NAME,
                    String.valueOf(PostVersion.ENTITY_VERSION),
                    id
            );

            UUID ret = deletedId.get();
            TechnicalIdResponse resp = new TechnicalIdResponse();
            resp.setTechnicalId(ret.toString());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deletePostVersion: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(cause.getMessage());
            } else {
                logger.error("ExecutionException in deletePostVersion", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting PostVersion", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception e) {
            logger.error("Unexpected error in deletePostVersion", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    // Helper to map request DTO to entity
    private PostVersion mapToEntity(CreatePostVersionRequest req) {
        PostVersion p = new PostVersion();
        p.setVersionId(req.getVersionId());
        p.setPostId(req.getPostId());
        p.setAuthorId(req.getAuthorId());
        p.setContentRich(req.getContentRich());
        p.setChangeSummary(req.getChangeSummary());
        p.setCreatedAt(req.getCreatedAt());
        p.setEmbeddingsRef(req.getEmbeddingsRef());
        p.setNormalizedText(req.getNormalizedText());
        p.setChunksMeta(req.getChunksMeta());
        return p;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // DTOs

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical UUID of the persisted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;
    }

    @Data
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical UUIDs for persisted entities")
        private List<String> technicalIds;
    }

    @Data
    public static class CreatePostVersionRequest {
        @Schema(description = "Version id (client provided or generated)", example = "pv-456")
        private String versionId;

        @Schema(description = "Reference to parent Post id", required = true, example = "post-123")
        private String postId;

        @Schema(description = "Author id", required = true, example = "user-123")
        private String authorId;

        @Schema(description = "Rich content (editor delta or HTML)", example = "<p>Hello</p>")
        private String contentRich;

        @Schema(description = "Plaintext normalized", example = "hello")
        private String normalizedText;

        @Schema(description = "Metadata about chunks (chunk refs or meta)", example = "[{\"start\":0,\"end\":100}]")
        private List<java.util.Map<String, Object>> chunksMeta;

        @Schema(description = "Embeddings reference (vector store ref)", example = "vec-123")
        private String embeddingsRef;

        @Schema(description = "Short summary of changes", example = "Initial draft")
        private String changeSummary;

        @Schema(description = "Creation timestamp ISO", example = "2025-08-27T12:34:56Z")
        private String createdAt;
    }
}