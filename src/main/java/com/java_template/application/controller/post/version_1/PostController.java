package com.java_template.application.controller.post.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.post.version_1.Post;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/post/v1/posts")
@Tag(name = "Post", description = "Post entity operations (proxy to EntityService). Business logic in workflows.")
public class PostController {

    private static final Logger logger = LoggerFactory.getLogger(PostController.class);

    private final EntityService entityService;

    public PostController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Post", description = "Persist a new Post entity and trigger workflows. Returns technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createPost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Post creation payload",
                    content = @Content(schema = @Schema(implementation = CreatePostRequest.class)))
            @RequestBody CreatePostRequest request) {
        try {
            Post post = new Post();
            // Map fields from request to entity - controller is a proxy and does no business logic.
            post.setTitle(request.getTitle());
            post.setSlug(request.getSlug());
            post.setSummary(request.getSummary());
            post.setLocale(request.getLocale());
            post.setAuthorId(request.getAuthorId());
            post.setTags(request.getTags());
            post.setCurrentVersionId(request.getCurrentVersionId());
            post.setPublishDatetime(request.getPublishDatetime());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Post.ENTITY_NAME,
                    String.valueOf(Post.ENTITY_VERSION),
                    post
            );

            UUID id = idFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(id.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPost", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createPost", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Post", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createPost", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Create multiple Posts", description = "Persist multiple Post entities and return their technicalIds.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdsResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/bulk")
    public ResponseEntity<TechnicalIdsResponse> createPostsBulk(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Bulk post creation payload",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = CreatePostRequest.class))))
            @RequestBody List<CreatePostRequest> requests) {
        try {
            List<Post> posts = new ArrayList<>();
            for (CreatePostRequest r : requests) {
                Post p = new Post();
                p.setTitle(r.getTitle());
                p.setSlug(r.getSlug());
                p.setSummary(r.getSummary());
                p.setLocale(r.getLocale());
                p.setAuthorId(r.getAuthorId());
                p.setTags(r.getTags());
                p.setCurrentVersionId(r.getCurrentVersionId());
                p.setPublishDatetime(r.getPublishDatetime());
                posts.add(p);
            }

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Post.ENTITY_NAME,
                    String.valueOf(Post.ENTITY_VERSION),
                    posts
            );

            List<UUID> ids = idsFuture.get();
            List<String> strIds = new ArrayList<>();
            for (UUID u : ids) strIds.add(u.toString());
            TechnicalIdsResponse resp = new TechnicalIdsResponse(strIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for createPostsBulk", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in createPostsBulk", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while creating Posts bulk", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in createPostsBulk", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get Post by technicalId", description = "Retrieve a Post by its technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = Post.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<ObjectNode> getPostById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Post.ENTITY_NAME,
                    String.valueOf(Post.ENTITY_VERSION),
                    id
            );
            ObjectNode node = itemFuture.get();
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for getPostById", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getPostById", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving Post", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getPostById", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get all Posts", description = "Retrieve all Posts.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Post.class)))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @GetMapping
    public ResponseEntity<ArrayNode> getAllPosts() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Post.ENTITY_NAME,
                    String.valueOf(Post.ENTITY_VERSION)
            );
            ArrayNode array = itemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in getAllPosts", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving Posts", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in getAllPosts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Search Posts by condition", description = "Retrieve posts matching a simple search condition.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = Post.class)))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PostMapping("/search")
    public ResponseEntity<ArrayNode> searchPosts(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Search condition payload",
                    content = @Content(schema = @Schema(implementation = SearchConditionRequest.class)))
            @RequestBody SearchConditionRequest condition) {
        try {
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Post.ENTITY_NAME,
                    String.valueOf(Post.ENTITY_VERSION),
                    condition,
                    true
            );
            ArrayNode array = filteredItemsFuture.get();
            return ResponseEntity.ok(array);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid search condition for searchPosts", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in searchPosts", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while searching Posts", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in searchPosts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Update Post", description = "Update an existing Post by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> updatePost(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Post update payload",
                    content = @Content(schema = @Schema(implementation = UpdatePostRequest.class)))
            @RequestBody UpdatePostRequest request) {
        try {
            UUID id = UUID.fromString(technicalId);
            Post post = new Post();
            // map fields (controller remains a proxy)
            post.setTitle(request.getTitle());
            post.setSlug(request.getSlug());
            post.setSummary(request.getSummary());
            post.setLocale(request.getLocale());
            post.setAuthorId(request.getAuthorId());
            post.setTags(request.getTags());
            post.setCurrentVersionId(request.getCurrentVersionId());
            post.setPublishDatetime(request.getPublishDatetime());

            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Post.ENTITY_NAME,
                    String.valueOf(Post.ENTITY_VERSION),
                    id,
                    post
            );
            UUID updatedId = updatedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(updatedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid request for updatePost", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in updatePost", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while updating Post", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in updatePost", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Delete Post", description = "Delete an existing Post by technicalId.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request", content = @Content),
            @ApiResponse(responseCode = "404", description = "Not Found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content)
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<TechnicalIdResponse> deletePost(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Post.ENTITY_NAME,
                    String.valueOf(Post.ENTITY_VERSION),
                    id
            );
            UUID deletedId = deletedIdFuture.get();
            TechnicalIdResponse resp = new TechnicalIdResponse(deletedId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException iae) {
            logger.warn("Invalid technicalId for deletePost", iae);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            } else {
                logger.error("ExecutionException in deletePost", ee);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while deleting Post", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error in deletePost", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Data
    @Schema(name = "CreatePostRequest", description = "Payload to create a Post")
    public static class CreatePostRequest {
        @Schema(description = "Post title", example = "How to use Cyoda")
        private String title;

        @Schema(description = "Canonical URL fragment", example = "how-to-use-cyoda")
        private String slug;

        @Schema(description = "Short summary", example = "Short summary")
        private String summary;

        @Schema(description = "Locale (e.g. en-GB)", example = "en-GB")
        private String locale;

        @JsonProperty("author_id")
        @Schema(name = "author_id", description = "Author technical id", example = "user-123")
        private String authorId;

        @Schema(description = "Tags", example = "[\"cyoda\",\"cms\"]")
        private List<String> tags;

        @JsonProperty("current_version_id")
        @Schema(name = "current_version_id", description = "Reference to current PostVersion", example = "pv-456")
        private String currentVersionId;

        @JsonProperty("publish_datetime")
        @Schema(name = "publish_datetime", description = "Scheduled publish datetime ISO", example = "2025-09-01T10:00:00Z")
        private String publishDatetime;
    }

    @Data
    @Schema(name = "UpdatePostRequest", description = "Payload to update a Post")
    public static class UpdatePostRequest {
        @Schema(description = "Post title", example = "How to use Cyoda")
        private String title;

        @Schema(description = "Canonical URL fragment", example = "how-to-use-cyoda")
        private String slug;

        @Schema(description = "Short summary", example = "Short summary")
        private String summary;

        @Schema(description = "Locale (e.g. en-GB)", example = "en-GB")
        private String locale;

        @JsonProperty("author_id")
        @Schema(name = "author_id", description = "Author technical id", example = "user-123")
        private String authorId;

        @Schema(description = "Tags", example = "[\"cyoda\",\"cms\"]")
        private List<String> tags;

        @JsonProperty("current_version_id")
        @Schema(name = "current_version_id", description = "Reference to current PostVersion", example = "pv-456")
        private String currentVersionId;

        @JsonProperty("publish_datetime")
        @Schema(name = "publish_datetime", description = "Scheduled publish datetime ISO", example = "2025-09-01T10:00:00Z")
        private String publishDatetime;
    }

    @Data
    @Schema(name = "TechnicalIdResponse", description = "Response containing the technical id of created entity")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical identifier of the persisted entity", example = "tech-post-001")
        private String technicalId;

        public TechnicalIdResponse() {}

        public TechnicalIdResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "TechnicalIdsResponse", description = "Response containing multiple technical ids")
    public static class TechnicalIdsResponse {
        @Schema(description = "List of technical identifiers of the persisted entities", example = "[\"id1\",\"id2\"]")
        private List<String> technicalIds;

        public TechnicalIdsResponse() {}

        public TechnicalIdsResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }
}