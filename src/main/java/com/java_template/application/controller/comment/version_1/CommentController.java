package com.java_template.application.controller.comment.version_1;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.comment.version_1.Comment;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/v1/comments")
@Tag(name = "Comment", description = "Comment entity proxy controller (version 1)")
public class CommentController {
    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);
    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    public CommentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Create a Comment", description = "Proxy endpoint to create a single Comment entity")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = AddResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping
    public ResponseEntity<?> createComment(
            @RequestBody(description = "Comment creation payload") @org.springframework.web.bind.annotation.RequestBody CommentRequest request) {
        try {
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Comment c = toEntity(request);
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Comment.ENTITY_NAME, String.valueOf(Comment.ENTITY_VERSION), c);
            UUID technicalId = idFuture.get();
            AddResponse resp = new AddResponse(technicalId.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
        }
    }

    @Operation(summary = "Create multiple Comments", description = "Proxy endpoint to create multiple Comment entities")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = BulkAddResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/bulk")
    public ResponseEntity<?> createCommentsBulk(
            @RequestBody(description = "List of comments to create") @org.springframework.web.bind.annotation.RequestBody List<CommentRequest> requests) {
        try {
            if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Request body must contain at least one item");
            List<Comment> entities = requests.stream().map(this::toEntity).toList();
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    Comment.ENTITY_NAME, String.valueOf(Comment.ENTITY_VERSION), entities);
            List<UUID> uuids = idsFuture.get();
            List<String> technicalIds = uuids.stream().map(UUID::toString).toList();
            BulkAddResponse resp = new BulkAddResponse(technicalIds);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
        }
    }

    @Operation(summary = "Get Comment by technicalId", description = "Retrieve a Comment by its technical UUID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/{technicalId}")
    public ResponseEntity<?> getCommentById(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Comment.ENTITY_NAME, String.valueOf(Comment.ENTITY_VERSION), UUID.fromString(technicalId));
            ObjectNode node = itemFuture.get();
            CommentResponse resp = mapper.convertValue(node, CommentResponse.class);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
        }
    }

    @Operation(summary = "Get Comments", description = "Retrieve all Comments or filter by postId query parameter")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @ArraySchema(schema = @Schema(implementation = CommentResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping
    public ResponseEntity<?> getComments(@RequestParam(value = "postId", required = false) String postId) {
        try {
            ArrayNode arrayNode;
            if (postId != null && !postId.isBlank()) {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.postId", "EQUALS", postId));
                CompletableFuture<ArrayNode> filteredFuture = entityService.getItemsByCondition(
                        Comment.ENTITY_NAME, String.valueOf(Comment.ENTITY_VERSION), condition, true);
                arrayNode = filteredFuture.get();
            } else {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Comment.ENTITY_NAME, String.valueOf(Comment.ENTITY_VERSION));
                arrayNode = itemsFuture.get();
            }
            List<CommentResponse> list = mapper.convertValue(arrayNode, new TypeReference<List<CommentResponse>>() {});
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
        }
    }

    @Operation(summary = "Update a Comment", description = "Proxy endpoint to update a Comment entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UpdateResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PutMapping("/{technicalId}")
    public ResponseEntity<?> updateComment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId,
            @RequestBody(description = "Comment update payload") @org.springframework.web.bind.annotation.RequestBody CommentRequest request) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            if (request == null) throw new IllegalArgumentException("Request body is required");
            Comment c = toEntity(request);
            CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                    Comment.ENTITY_NAME, String.valueOf(Comment.ENTITY_VERSION), UUID.fromString(technicalId), c);
            UUID updated = updatedIdFuture.get();
            UpdateResponse resp = new UpdateResponse(updated.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
        }
    }

    @Operation(summary = "Delete a Comment", description = "Proxy endpoint to delete a Comment entity by technicalId")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DeleteResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad Request"),
        @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @DeleteMapping("/{technicalId}")
    public ResponseEntity<?> deleteComment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(
                    Comment.ENTITY_NAME, String.valueOf(Comment.ENTITY_VERSION), UUID.fromString(technicalId));
            UUID deleted = deletedIdFuture.get();
            DeleteResponse resp = new DeleteResponse(deleted.toString());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException ee) {
            return handleExecutionException(ee);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted", ie);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Interrupted");
        } catch (Exception ex) {
            logger.error("Unexpected error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error");
        }
    }

    private Comment toEntity(CommentRequest req) {
        Comment c = new Comment();
        c.setId(req.getId());
        c.setBody(req.getBody());
        c.setEmail(req.getEmail());
        c.setFetchedAt(req.getFetchedAt());
        c.setName(req.getName());
        // map post_id -> postId (entity uses String)
        c.setPostId(req.getPostId() == null ? null : String.valueOf(req.getPostId()));
        c.setSource(req.getSource());
        return c;
    }

    private ResponseEntity<String> handleExecutionException(ExecutionException ee) {
        Throwable cause = ee.getCause();
        if (cause instanceof NoSuchElementException) {
            logger.warn("Not found: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
        } else if (cause instanceof IllegalArgumentException) {
            logger.warn("Bad request from service: {}", cause.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
        } else {
            logger.error("Service execution error", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Service execution error");
        }
    }

    // DTOs

    @Data
    @Schema(name = "CommentRequest", description = "Request payload to create/update a Comment")
    public static class CommentRequest {
        @Schema(description = "Comment id from source API", example = "1")
        private Integer id;

        @Schema(description = "Post id (foreign key)", example = "1")
        private Integer postId;

        @Schema(description = "Commenter name", example = "id labore ex et quam laborum")
        private String name;

        @Schema(description = "Commenter email", example = "Eliseo@gardner.biz")
        private String email;

        @Schema(description = "Comment body", example = "some text")
        private String body;

        @Schema(description = "When comment was fetched (ISO timestamp)", example = "2025-08-26T12:00:05Z")
        private String fetchedAt;

        @Schema(description = "Source URI", example = "https://jsonplaceholder.typicode.com/comments")
        private String source;
    }

    @Data
    @Schema(name = "CommentResponse", description = "Response representation of a Comment")
    public static class CommentResponse {
        @Schema(description = "Comment id from source API", example = "1")
        private Integer id;

        @Schema(description = "Post id (foreign key)", example = "1")
        private String postId;

        @Schema(description = "Commenter name", example = "id labore ex et quam laborum")
        private String name;

        @Schema(description = "Commenter email", example = "Eliseo@gardner.biz")
        private String email;

        @Schema(description = "Comment body", example = "some text")
        private String body;

        @Schema(description = "When comment was fetched (ISO timestamp)", example = "2025-08-26T12:00:05Z")
        private String fetchedAt;

        @Schema(description = "Source URI", example = "https://jsonplaceholder.typicode.com/comments")
        private String source;
    }

    @Data
    @Schema(name = "AddResponse", description = "Response containing technicalId for created entity")
    public static class AddResponse {
        @Schema(description = "Technical ID of created entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public AddResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "BulkAddResponse", description = "Response containing technicalIds for created entities")
    public static class BulkAddResponse {
        @Schema(description = "List of technical IDs")
        private List<String> technicalIds;

        public BulkAddResponse(List<String> technicalIds) {
            this.technicalIds = technicalIds;
        }
    }

    @Data
    @Schema(name = "UpdateResponse", description = "Response containing technicalId for updated entity")
    public static class UpdateResponse {
        @Schema(description = "Technical ID of updated entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public UpdateResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    @Schema(name = "DeleteResponse", description = "Response containing technicalId for deleted entity")
    public static class DeleteResponse {
        @Schema(description = "Technical ID of deleted entity", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        private String technicalId;

        public DeleteResponse(String technicalId) {
            this.technicalId = technicalId;
        }
    }
}