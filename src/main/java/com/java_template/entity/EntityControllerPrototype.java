package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, IngestionTask> ingestionTasks = new ConcurrentHashMap<>();
    private final Map<String, AggregatePage> aggregates = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private List<LanguageConfig> languages;
    private static final long MAX_TIME_SPAN_SECONDS = 60L * 60 * 24 * 365 * 2; // TODO: read from app properties

    @PostConstruct
    public void initLanguages() {
        languages = List.of(
                new LanguageConfig("Java", Set.of("java")),
                new LanguageConfig("Kotlin", Set.of("kotlin")),
                new LanguageConfig("Python", Set.of("python","py")),
                new LanguageConfig("Go", Set.of("go","golang")),
                new LanguageConfig("Rust", Set.of("rust"))
        );
    }

    @PostMapping("/ingestion-tasks")
    public ResponseEntity<IngestionTaskResponse> createIngestionTask(
            @RequestBody @Valid IngestionTaskRequest request) {
        log.info("Received ingestion task request: startDate={}, endDate={}", request.getStartDate(), request.getEndDate());

        Instant startInstant;
        Instant endInstant;
        try {
            startInstant = OffsetDateTime.parse(request.getStartDate()).toInstant();
            endInstant = OffsetDateTime.parse(request.getEndDate()).toInstant();
        } catch (DateTimeParseException e) {
            log.error("Invalid date format", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format");
        }
        if (endInstant.isBefore(startInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be after startDate");
        }
        long requestedSpan = endInstant.getEpochSecond() - startInstant.getEpochSecond();
        if (requestedSpan > MAX_TIME_SPAN_SECONDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The requested time window exceeds the maximum allowed span.");
        }

        String taskId = UUID.randomUUID().toString();
        IngestionTask task = new IngestionTask(taskId, "initialized", startInstant, endInstant, 0);
        ingestionTasks.put(taskId, task);
        logger.info("Created ingestion task with id {}", taskId);
        CompletableFuture.runAsync(() -> runIngestionTask(task)); // fire-and-forget
        return ResponseEntity.ok(new IngestionTaskResponse(taskId, task.getStatus(), "Ingestion task created and started"));
    }

    @GetMapping("/ingestion-tasks/{taskId}/status")
    public ResponseEntity<IngestionTaskStatus> getIngestionTaskStatus(@PathVariable String taskId) {
        IngestionTask task = ingestionTasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion task not found");
        }
        return ResponseEntity.ok(new IngestionTaskStatus(taskId, task.getStatus(), task.getProgress()));
    }

    @PostMapping("/ingestion-tasks/{taskId}/abort")
    public ResponseEntity<AbortResponse> abortIngestionTask(@PathVariable String taskId) {
        IngestionTask task = ingestionTasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingestion task not found");
        }
        if ("completed".equals(task.getStatus()) || "failed".equals(task.getStatus()) || "aborted".equals(task.getStatus())) {
            return ResponseEntity.badRequest().body(new AbortResponse(taskId, task.getStatus(), "Task cannot be aborted in current state"));
        }
        task.setStatus("aborting");
        logger.info("Abort requested for ingestion task {}", taskId);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(1000);
                task.setStatus("aborted");
                logger.info("Ingestion task {} aborted", taskId);
            } catch (InterruptedException ignored) {
            }
        });
        return ResponseEntity.ok(new AbortResponse(taskId, "aborting", "Abort request accepted"));
    }

    @GetMapping("/aggregates")
    public ResponseEntity<AggregatePage> queryAggregates(
            @RequestParam @NotBlank String language,
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", message = "startDate must be ISO 8601")
            String startDate,
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", message = "endDate must be ISO 8601")
            String endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size
    ) {
        logger.info("Query aggregates for language={} startDate={} endDate={} page={} size={}", language, startDate, endDate, page, size);
        List<AggregateEntry> allEntries = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            allEntries.add(new AggregateEntry(language, "2023-01-" + String.format("%02d", i + 1), 10 + i));
        }
        int fromIndex = Math.min(page * size, allEntries.size());
        int toIndex = Math.min(fromIndex + size, allEntries.size());
        List<AggregateEntry> pageContent = allEntries.subList(fromIndex, toIndex);
        AggregatePage result = new AggregatePage(pageContent, page, size, allEntries.size(), (allEntries.size() + size - 1) / size);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/comments/analyze")
    public ResponseEntity<LanguageDetectionResponse> analyzeComment(
            @RequestBody @Valid CommentAnalyzeRequest request) {
        logger.info("Analyze comment text via LLM (mock): {}", request.getCommentText());
        Set<String> detected = new HashSet<>();
        String textLower = request.getCommentText().toLowerCase(Locale.ROOT);
        for (LanguageConfig lang : languages) {
            for (String alias : lang.getAliases()) {
                if (textLower.contains(alias.toLowerCase(Locale.ROOT))) {
                    detected.add(lang.getName());
                    break;
                }
            }
        }
        return ResponseEntity.ok(new LanguageDetectionResponse(new ArrayList<>(detected)));
    }

    @Async
    public void runIngestionTask(IngestionTask task) {
        try {
            logger.info("Starting ingestion task {}", task.getTaskId());
            task.setStatus("fetching_ids");
            int maxItemId = fetchMaxItemId();
            List<Integer> commentIds = fetchCommentIdsInRange(task.getStartDate(), task.getEndDate(), maxItemId);
            task.setStatus("fetching_comments");
            int processed = 0;
            for (Integer commentId : commentIds) {
                if ("aborting".equals(task.getStatus())) {
                    logger.info("Ingestion task {} abort detected, stopping", task.getTaskId());
                    task.setStatus("aborted");
                    return;
                }
                JsonNode commentJson = fetchCommentById(commentId);
                if (commentJson == null) {
                    logger.error("Failed to fetch comment {}", commentId);
                    continue;
                }
                String text = commentJson.hasNonNull("text") ? commentJson.get("text").asText() : "";
                if (text.isBlank()) {
                    continue;
                }
                LanguageDetectionResponse detection = analyzeComment(new CommentAnalyzeRequest(text)).getBody();
                logger.info("Comment {} detected languages: {}", commentId, detection.getDetectedLanguages());
                processed++;
                task.setProgress((int) ((processed / (double) commentIds.size()) * 100));
            }
            task.setStatus("completed");
            task.setProgress(100);
            logger.info("Ingestion task {} completed", task.getTaskId());
        } catch (Exception e) {
            task.setStatus("failed");
            logger.error("Ingestion task {} failed with exception", task.getTaskId(), e);
        }
    }

    private int fetchMaxItemId() {
        try {
            String url = "https://hacker-news.firebaseio.com/v0/maxitem.json";
            String response = restTemplate.getForObject(new URI(url), String.class);
            JsonNode node = objectMapper.readTree(response);
            return node.asInt();
        } catch (Exception e) {
            logger.error("Failed to fetch max item id from Firebase", e);
            return 1000000;
        }
    }

    private List<Integer> fetchCommentIdsInRange(Instant start, Instant end, int maxItemId) {
        List<Integer> ids = new ArrayList<>();
        for (int i = maxItemId; i > maxItemId - 50; i--) {
            ids.add(i);
        }
        return ids;
    }

    private JsonNode fetchCommentById(int commentId) {
        try {
            String url = "https://hacker-news.firebaseio.com/v0/item/" + commentId + ".json";
            String response = restTemplate.getForObject(new URI(url), String.class);
            if (response == null) return null;
            JsonNode node = objectMapper.readTree(response);
            if (!"comment".equals(node.path("type").asText())) {
                return null;
            }
            return node;
        } catch (Exception e) {
            logger.error("Failed to fetch comment id {}", commentId, e);
            return null;
        }
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    private static class IngestionTaskRequest {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", message = "startDate must be ISO 8601")
        private String startDate;

        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", message = "endDate must be ISO 8601")
        private String endDate;
    }

    @Data @AllArgsConstructor
    private static class IngestionTaskResponse {
        private String taskId;
        private String status;
        private String message;
    }

    @Data @AllArgsConstructor
    private static class IngestionTaskStatus {
        private String taskId;
        private String status;
        private int progress;
    }

    @Data @AllArgsConstructor
    private static class AbortResponse {
        private String taskId;
        private String status;
        private String message;
    }

    @Data
    private static class IngestionTask {
        private final String taskId;
        private String status;
        private final Instant startDate;
        private final Instant endDate;
        private int progress;
    }

    @Data @AllArgsConstructor
    private static class LanguageConfig {
        private String name;
        private Set<String> aliases;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    private static class CommentAnalyzeRequest {
        @NotBlank
        @Size(min = 1, max = 10000)
        private String commentText;
    }

    @Data @AllArgsConstructor
    private static class LanguageDetectionResponse {
        private List<String> detectedLanguages;
    }

    @Data @AllArgsConstructor
    private static class AggregateEntry {
        private String language;
        private String timeSlice;
        private int mentionCount;
    }

    @Data @AllArgsConstructor
    private static class AggregatePage {
        private List<AggregateEntry> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled exception: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("code", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }
}