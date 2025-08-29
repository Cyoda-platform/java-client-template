package com.java_template.application.controller.post.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.post.version_1.Post;
import com.java_template.common.service.EntityService;
import lombok.Data;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/posts")
@Tag(name = "Post Controller", description = "CRUD proxy endpoints for Post entity (version 1)")
public class PostController {

    private static final Logger logger = LoggerFactory.getLogger(PostController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PostController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create Post", description = "Persist a new Post entity and start associated workflows. Returns only the technicalId.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<TechnicalIdResponse> createPost(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Post create payload")
            @RequestBody CreatePostRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("Request body is required");
            }
            if (request.getTitle() == null || request.getTitle().isBlank()) {
                throw new IllegalArgumentException("title is required");
            }
            if (request.getSlug() == null || request.getSlug().isBlank()) {
                throw new IllegalArgumentException("slug is required");
            }

            Post post = new Post();
            // Basic assignment only. No business logic here.
            post.setId(UUID.randomUUID().toString());
            // owner_id fallback: prefer provided owner_id, then author_id, else "system"
            if (request.getOwner_id() != null && !request.getOwner_id().isBlank()) {
                post.setOwner_id(request.getOwner_id());
            } else if (request.getAuthor_id() != null && !request.getAuthor_id().isBlank()) {
                post.setOwner_id(request.getAuthor_id());
            } else {
                post.setOwner_id("system");
            }
            // minimal required fields
            post.setTitle(request.getTitle());
            post.setSlug(request.getSlug());
            post.setSummary(request.getSummary());
            post.setLocale(request.getLocale());
            post.setAuthor_id(request.getAuthor_id());
            post.setCurrent_version_id(request.getCurrent_version_id());
            post.setPublish_datetime(request.getPublish_datetime());
            post.setTags(request.getTags());
            post.setMedia_refs(request.getMedia_refs());
            // set default status to "draft" if not provided to satisfy basic entity constraints
            post.setStatus(request.getStatus() != null && !request.getStatus().isBlank() ? request.getStatus() : "draft");
            post.setCache_control(request.getCache_control());

            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                    Post.ENTITY_NAME,
                    Post.ENTITY_VERSION,
                    post
            );
            UUID technicalId = idFuture.get();
            TechnicalIdResponse response = new TechnicalIdResponse();
            response.setTechnicalId(technicalId.toString());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid createPost request: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Entity not found during create: {}", cause.getMessage(), cause);
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument from service during create: {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during createPost", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during createPost", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Get Post by technicalId", description = "Retrieve a persisted Post entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK"),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<PostResponse> getPostById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity")
            @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) {
                throw new IllegalArgumentException("technicalId is required");
            }
            CompletableFuture<DataPayload> itemFuture = entityService.getItem(UUID.fromString(technicalId));
            DataPayload dataPayload = itemFuture.get();
            ObjectNode node = dataPayload != null ? (ObjectNode) dataPayload.getData() : null;
            if (node == null) {
                return ResponseEntity.notFound().build();
            }
            PostResponse response = objectMapper.treeToValue((JsonNode) node, PostResponse.class);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid getPostById request: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NoSuchElementException) {
                logger.warn("Post not found: {}", cause.getMessage(), cause);
                return ResponseEntity.notFound().build();
            } else if (cause instanceof IllegalArgumentException) {
                logger.warn("Invalid argument from service during get: {}", cause.getMessage(), cause);
                return ResponseEntity.badRequest().build();
            } else {
                logger.error("ExecutionException during getPostById", ex);
                return ResponseEntity.status(500).build();
            }
        } catch (Exception ex) {
            logger.error("Unexpected error during getPostById", ex);
            return ResponseEntity.status(500).build();
        }
    }

    @Data
    @Schema(description = "Create Post request payload")
    public static class CreatePostRequest {
        @Schema(description = "Headline/title")
        private String title;
        @Schema(description = "Canonical URL fragment")
        private String slug;
        @Schema(description = "Short summary")
        private String summary;
        @Schema(description = "Locale, e.g. en-GB")
        private String locale;
        @Schema(description = "Author id")
        private String author_id;
        @Schema(description = "Owner id (optional)")
        private String owner_id;
        @Schema(description = "Current PostVersion id")
        private String current_version_id;
        @Schema(description = "Publish datetime (ISO)")
        private String publish_datetime;
        @Schema(description = "Tags")
        private List<String> tags;
        @Schema(description = "Media references")
        private List<String> media_refs;
        @Schema(description = "Status (optional, defaults to draft)")
        private String status;
        @Schema(description = "Cache control directive (optional)")
        private String cache_control;
    }

    @Data
    @Schema(description = "Technical Id response")
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of created entity")
        private String technicalId;
    }

    @Data
    @Schema(description = "Post response payload (mirrors Post entity)")
    public static class PostResponse {
        @Schema(description = "Canonical id")
        private String id;
        @Schema(description = "Author id")
        private String author_id;
        @Schema(description = "Owner id")
        private String owner_id;
        @Schema(description = "Current PostVersion id")
        private String current_version_id;
        @Schema(description = "Cache control")
        private String cache_control;
        @Schema(description = "Locale")
        private String locale;
        @Schema(description = "Publish datetime")
        private String publish_datetime;
        @Schema(description = "Published at timestamp")
        private String published_at;
        @Schema(description = "Slug")
        private String slug;
        @Schema(description = "Status")
        private String status;
        @Schema(description = "Summary")
        private String summary;
        @Schema(description = "Title")
        private String title;
        @Schema(description = "Media references")
        private List<String> media_refs;
        @Schema(description = "Tags")
        private List<String> tags;
    }
}