package com.java_template.application.controller;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.comment.version_1.Comment;
import com.java_template.application.entity.commentanalysisreport.version_1.CommentAnalysisReport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api")
@Tag(name = "Controller", description = "Event-driven REST API Controller for Job, Comment, and CommentAnalysisReport entities")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /jobs - Create a Job to fetch and analyze comments by postId
    @Operation(summary = "Create Job", description = "Create a Job to fetch and analyze comments by postId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job Created", content = @Content(schema = @Schema(implementation = TechnicalIdResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid Request"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody JobCreateRequest request) {
        try {
            Job job = new Job();
            job.setPostId(request.getPostId());
            job.setStatus("PENDING");
            job.setCreatedAt(null); // The createdAt will be set by the system or workflow

            CompletableFuture<UUID> idFuture = entityService.addItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                job
            );

            UUID technicalId = idFuture.get();

            TechnicalIdResponse response = new TechnicalIdResponse();
            response.setTechnicalId(technicalId.toString());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution exception in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in createJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /jobs/{technicalId} - Retrieve Job details by technicalId
    @Operation(summary = "Get Job", description = "Retrieve Job details by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Job Retrieved", content = @Content(schema = @Schema(implementation = Job.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Job Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/jobs/{technicalId}")
    public ResponseEntity<?> getJob(
        @Parameter(name = "technicalId", description = "Technical ID of the Job")
        @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Job.ENTITY_NAME,
                String.valueOf(Job.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode jobNode = itemFuture.get();
            if (jobNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Job not found");
            }
            return ResponseEntity.ok(jobNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution exception in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /comments/{technicalId} - Retrieve Comment details by technicalId
    @Operation(summary = "Get Comment", description = "Retrieve Comment details by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Comment Retrieved", content = @Content(schema = @Schema(implementation = Comment.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Comment Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/comments/{technicalId}")
    public ResponseEntity<?> getComment(
        @Parameter(name = "technicalId", description = "Technical ID of the Comment")
        @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Comment.ENTITY_NAME,
                String.valueOf(Comment.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode commentNode = itemFuture.get();
            if (commentNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found");
            }
            return ResponseEntity.ok(commentNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getComment", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution exception in getComment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getComment", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // GET /commentAnalysisReports/{technicalId} - Retrieve CommentAnalysisReport details by technicalId
    @Operation(summary = "Get CommentAnalysisReport", description = "Retrieve CommentAnalysisReport details by technicalId")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Report Retrieved", content = @Content(schema = @Schema(implementation = CommentAnalysisReport.class))),
        @ApiResponse(responseCode = "400", description = "Invalid technicalId"),
        @ApiResponse(responseCode = "404", description = "Report Not Found"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
    })
    @GetMapping("/commentAnalysisReports/{technicalId}")
    public ResponseEntity<?> getCommentAnalysisReport(
        @Parameter(name = "technicalId", description = "Technical ID of the CommentAnalysisReport")
        @PathVariable String technicalId) {
        try {
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                CommentAnalysisReport.ENTITY_NAME,
                String.valueOf(CommentAnalysisReport.ENTITY_VERSION),
                UUID.fromString(technicalId)
            );

            ObjectNode reportNode = itemFuture.get();
            if (reportNode == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CommentAnalysisReport not found");
            }
            return ResponseEntity.ok(reportNode);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getCommentAnalysisReport", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NoSuchElementException) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(cause.getMessage());
            } else if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cause.getMessage());
            }
            logger.error("Execution exception in getCommentAnalysisReport", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(cause.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error in getCommentAnalysisReport", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Data
    public static class JobCreateRequest {
        @Schema(description = "Identifier of the post to fetch comments for", example = "1", required = true)
        private Long postId;
    }

    @Data
    public static class TechnicalIdResponse {
        @Schema(description = "Technical ID of the entity", example = "550e8400-e29b-41d4-a716-446655440000")
        private String technicalId;
    }
}