package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.CommentIngestionJob;
import com.java_template.application.entity.Comment;
import com.java_template.application.entity.CommentAnalysisReport;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, CommentIngestionJob> commentIngestionJobCache = new ConcurrentHashMap<>();
    private final AtomicLong commentIngestionJobIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, Comment> commentCache = new ConcurrentHashMap<>();
    private final AtomicLong commentIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, CommentAnalysisReport> commentAnalysisReportCache = new ConcurrentHashMap<>();
    private final AtomicLong commentAnalysisReportIdCounter = new AtomicLong(1);

    // --- CommentIngestionJob Endpoints ---

    @PostMapping("/commentIngestionJob")
    public ResponseEntity<?> createCommentIngestionJob(@RequestBody CommentIngestionJob job) {
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

        String id = String.valueOf(commentIngestionJobIdCounter.getAndIncrement());
        job.setId(id);
        job.setStatus("PENDING");
        job.setRequestedAt(java.time.LocalDateTime.now());
        commentIngestionJobCache.put(id, job);

        processCommentIngestionJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", job.getId(), "status", job.getStatus()));
    }

    @GetMapping("/commentIngestionJob/{id}")
    public ResponseEntity<?> getCommentIngestionJob(@PathVariable String id) {
        CommentIngestionJob job = commentIngestionJobCache.get(id);
        if (job == null) {
            log.error("CommentIngestionJob not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CommentIngestionJob not found");
        }
        return ResponseEntity.ok(job);
    }

    // --- Comment Endpoints ---

    @PostMapping("/comment")
    public ResponseEntity<?> createComment(@RequestBody Comment comment) {
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

        String id = comment.getId(); // Use provided business ID
        comment.setId(id);
        comment.setStatus("RAW");
        commentCache.put(id, comment);

        processComment(comment);

        return ResponseEntity.status(HttpStatus.CREATED).body(comment);
    }

    @GetMapping("/comment/{id}")
    public ResponseEntity<?> getComment(@PathVariable String id) {
        Comment comment = commentCache.get(id);
        if (comment == null) {
            log.error("Comment not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Comment not found");
        }
        return ResponseEntity.ok(comment);
    }

    // --- CommentAnalysisReport Endpoints ---

    @PostMapping("/commentAnalysisReport")
    public ResponseEntity<?> createCommentAnalysisReport(@RequestBody CommentAnalysisReport report) {
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

        String id = report.getId();
        report.setId(id);
        report.setStatus("CREATED");
        report.setGeneratedAt(java.time.LocalDateTime.now());
        commentAnalysisReportCache.put(id, report);

        processCommentAnalysisReport(report);

        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    @GetMapping("/commentAnalysisReport/{id}")
    public ResponseEntity<?> getCommentAnalysisReport(@PathVariable String id) {
        CommentAnalysisReport report = commentAnalysisReportCache.get(id);
        if (report == null) {
            log.error("CommentAnalysisReport not found with id: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("CommentAnalysisReport not found");
        }
        return ResponseEntity.ok(report);
    }

    // --- Processing methods ---

    private void processCommentIngestionJob(CommentIngestionJob job) {
        log.info("Processing CommentIngestionJob with ID: {}", job.getId());
        // Validate job fields
        if (job.getPostId() == null || job.getReportEmail() == null || job.getReportEmail().isBlank()) {
            log.error("Invalid CommentIngestionJob: missing postId or reportEmail");
            job.setStatus("FAILED");
            return;
        }
        job.setStatus("PROCESSING");
        commentIngestionJobCache.put(job.getId(), job);

        // Simulate fetching comments from external API
        // Here, just simulate creation of some dummy comments for demonstration purposes
        for (int i = 1; i <= 3; i++) {
            Comment comment = new Comment();
            comment.setId(UUID.randomUUID().toString());
            comment.setPostId(job.getPostId());
            comment.setName("User " + i);
            comment.setEmail("user" + i + "@example.com");
            comment.setBody("Sample comment body " + i);
            comment.setIngestionJobId(job.getId());
            comment.setStatus("RAW");
            commentCache.put(comment.getId(), comment);
            processComment(comment);
        }

        // Trigger report generation after comments are ingested
        CommentAnalysisReport report = new CommentAnalysisReport();
        report.setId(UUID.randomUUID().toString());
        report.setIngestionJobId(job.getId());
        report.setKeywordCounts(new HashMap<>());
        report.setTotalComments(3);
        report.setSentimentSummary("Neutral");
        report.setStatus("CREATED");
        report.setGeneratedAt(java.time.LocalDateTime.now());
        commentAnalysisReportCache.put(report.getId(), report);

        processCommentAnalysisReport(report);

        job.setStatus("COMPLETED");
        job.setCompletedAt(java.time.LocalDateTime.now());
        commentIngestionJobCache.put(job.getId(), job);

        // Simulate sending notification email
        log.info("Sending report email to {}", job.getReportEmail());
    }

    private void processComment(Comment comment) {
        log.info("Processing Comment with ID: {}", comment.getId());
        // Example: mark comment as analyzed immediately for demo
        comment.setStatus("ANALYZED");
        commentCache.put(comment.getId(), comment);
    }

    private void processCommentAnalysisReport(CommentAnalysisReport report) {
        log.info("Processing CommentAnalysisReport with ID: {}", report.getId());
        // Example: simulate sending email and mark report as sent
        report.setStatus("SENT");
        commentAnalysisReportCache.put(report.getId(), report);
        log.info("Report sent for CommentAnalysisReport ID: {}", report.getId());
    }
}