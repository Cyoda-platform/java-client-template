package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletableFuture;
import java.util.List;

@Tag(name = "Comment API", description = "Operations to retrieve and manage comments")
@RestController
@RequestMapping("/api/comments")
@Validated
public class CommentController {
    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CommentController(EntityService entityService) {
        this.entityService = entityService;
    }

    @Operation(summary = "Get comments for a post", description = "List comments for a given postId with pagination")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommentListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CommentListResponse> listComments(
            @RequestParam(name = "postId") Integer postId,
            @RequestParam(name = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "50") Integer pageSize
    ) {
        try {
            if (postId == null || postId <= 0) throw new IllegalArgumentException("postId is required and must be positive");

            // Build simple search condition for postId
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.postId", "EQUALS", String.valueOf(postId))
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Comment.ENTITY_NAME,
                    String.valueOf(Comment.ENTITY_VERSION),
                    condition,
                    true
            );

            ArrayNode items = itemsFuture.get();

            CommentListResponse resp = new CommentListResponse();
            resp.setItems(items);
            resp.setPage(page);
            resp.setPageSize(pageSize);
            resp.setTotal(items.size());

            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for listComments: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while listing comments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error listing comments", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "Get comment by technicalId", description = "Retrieve a single Comment by technicalId")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Comment.class))),
            @ApiResponse(responseCode = "404", description = "Not Found"),
            @ApiResponse(responseCode = "400", description = "Bad Request"),
            @ApiResponse(responseCode = "500", description = "Server Error")
    })
    @GetMapping(value = "/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ObjectNode> getComment(
            @Parameter(name = "technicalId", description = "Technical ID of the entity") @PathVariable String technicalId
    ) {
        try {
            if (technicalId == null || technicalId.isBlank()) throw new IllegalArgumentException("technicalId is required");
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Comment.ENTITY_NAME,
                    String.valueOf(Comment.ENTITY_VERSION),
                    UUID.fromString(technicalId)
            );
            ObjectNode item = itemFuture.get();
            return ResponseEntity.ok(item);
        } catch (IllegalArgumentException e) {
            logger.warn("Bad request for getComment: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (ExecutionException e) {
            return handleExecutionException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while retrieving comment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Unexpected error retrieving comment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity handleExecutionException(ExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof NoSuchElementException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().build();
        }
        logger.error("ExecutionException in CommentController", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @Data
    public static class CommentListResponse {
        @Schema(description = "List of comments")
        private ArrayNode items;
        @Schema(description = "Page number")
        private Integer page;
        @Schema(description = "Page size")
        private Integer pageSize;
        @Schema(description = "Total items")
        private Integer total;
    }
}
