package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Comment;
import com.java_template.application.entity.CommentAnalysisReport;
import com.java_template.application.entity.CommentIngestionJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String COMMENT_INGESTION_JOB_ENTITY = "CommentIngestionJob";
    private static final String COMMENT_ENTITY = "Comment";
    private static final String COMMENT_ANALYSIS_REPORT_ENTITY = "CommentAnalysisReport";

    // --- CommentIngestionJob Endpoints ---

    @PostMapping("/commentIngestionJob")
    public ResponseEntity<?> createCommentIngestionJob(@RequestBody CommentIngestionJob job) throws Exception {
        log.info("Received create CommentIngestionJob request: {}", job);
        if (job == null) {
            log.error("Request body is null");
            return ResponseEntity.badRequest().body("Request body is required");
        }
        if (job.getPostId() == null) {
            log.error("postId is missing");
            return ResponseEntity.badRequest().body("postId is required");
        }
        if (job.getReportEmail() == null || job.getReportEmail().isBlank()) {
            log.error("reportEmail is missing or blank");
            return ResponseEntity.badRequest().body("reportEmail is required");
        }

        job.setStatus("PENDING");
        job.setRequestedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(COMMENT_INGESTION_JOB_ENTITY, ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();

        // Removed processCommentIngestionJob call

        Map<String, Object> response = new HashMap<>();
        response.put("technicalId", technicalId.toString());
        response.put("status", job.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/commentIngestionJob/{id}")
    public ResponseEntity<?> getCommentIngestionJob(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(COMMENT_INGESTION_JOB_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("CommentIngestionJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CommentIngestionJob not found");
        }
        return ResponseEntity.ok(item);
    }

    // --- Comment Endpoints ---

    @PostMapping("/comment")
    public ResponseEntity<?> createComment(@RequestBody Comment comment) throws Exception {
        log.info("Received create Comment request: {}", comment);
        if (comment == null) {
            log.error("Request body is null");
            return ResponseEntity.badRequest().body("Request body is required");
        }
        if (comment.getId() == null || comment.getId().isBlank()) {
            log.error("Comment id is missing or blank");
            return ResponseEntity.badRequest().body("Comment id is required");
        }
        if (comment.getPostId() == null) {
            log.error("postId is missing");
            return ResponseEntity.badRequest().body("postId is required");
        }
        if (comment.getName() == null || comment.getName().isBlank()) {
            log.error("name is missing or blank");
            return ResponseEntity.badRequest().body("name is required");
        }
        if (comment.getEmail() == null || comment.getEmail().isBlank()) {
            log.error("email is missing or blank");
            return ResponseEntity.badRequest().body("email is required");
        }
        if (comment.getBody() == null || comment.getBody().isBlank()) {
            log.error("body is missing or blank");
            return ResponseEntity.badRequest().body("body is required");
        }
        if (comment.getIngestionJobId() == null || comment.getIngestionJobId().isBlank()) {
            log.error("ingestionJobId is missing or blank");
            return ResponseEntity.badRequest().body("ingestionJobId is required");
        }

        // In original code business id was used as id, here we pass the entire comment to entityService and get UUID technicalId
        comment.setStatus("RAW");

        CompletableFuture<UUID> idFuture = entityService.addItem(COMMENT_ENTITY, ENTITY_VERSION, comment);
        UUID technicalId = idFuture.get();

        // Removed processComment call

        // Return the created comment with technicalId added
        ObjectNode createdNode = entityService.getItem(COMMENT_ENTITY, ENTITY_VERSION, technicalId).get();

        return ResponseEntity.status(HttpStatus.CREATED).body(createdNode);
    }

    @GetMapping("/comment/{id}")
    public ResponseEntity<?> getComment(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(COMMENT_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("Comment not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found");
        }
        return ResponseEntity.ok(item);
    }

    // --- CommentAnalysisReport Endpoints ---

    @PostMapping("/commentAnalysisReport")
    public ResponseEntity<?> createCommentAnalysisReport(@RequestBody CommentAnalysisReport report) throws Exception {
        log.info("Received create CommentAnalysisReport request: {}", report);
        if (report == null) {
            log.error("Request body is null");
            return ResponseEntity.badRequest().body("Request body is required");
        }
        if (report.getId() == null || report.getId().isBlank()) {
            log.error("Report id is missing or blank");
            return ResponseEntity.badRequest().body("Report id is required");
        }
        if (report.getIngestionJobId() == null || report.getIngestionJobId().isBlank()) {
            log.error("ingestionJobId is missing or blank");
            return ResponseEntity.badRequest().body("ingestionJobId is required");
        }
        if (report.getKeywordCounts() == null) {
            log.error("keywordCounts is missing");
            return ResponseEntity.badRequest().body("keywordCounts is required");
        }
        if (report.getTotalComments() == null) {
            log.error("totalComments is missing");
            return ResponseEntity.badRequest().body("totalComments is required");
        }

        report.setStatus("CREATED");
        report.setGeneratedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(COMMENT_ANALYSIS_REPORT_ENTITY, ENTITY_VERSION, report);
        UUID technicalId = idFuture.get();

        // Removed processCommentAnalysisReport call

        ObjectNode createdNode = entityService.getItem(COMMENT_ANALYSIS_REPORT_ENTITY, ENTITY_VERSION, technicalId).get();

        return ResponseEntity.status(HttpStatus.CREATED).body(createdNode);
    }

    @GetMapping("/commentAnalysisReport/{id}")
    public ResponseEntity<?> getCommentAnalysisReport(@PathVariable String id) throws Exception {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid id format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(COMMENT_ANALYSIS_REPORT_ENTITY, ENTITY_VERSION, technicalId);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            log.error("CommentAnalysisReport not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CommentAnalysisReport not found");
        }
        return ResponseEntity.ok(item);
    }

    // Removed process methods
}