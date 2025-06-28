```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/prototype")
public class EntityControllerPrototype {

    private final Map<String, CommentIngestionTask> ingestionTasks = new ConcurrentHashMap<>();
    private final Map<String, Comment> comments = new ConcurrentHashMap<>();
    private final Map<String, LanguageMentions> languageMentionsStore = new ConcurrentHashMap<>();
    private final Map<String, LanguageMentionAggregate> aggregates = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Configurable batch size and languages loaded at startup (hardcoded here for prototype)
    private final int batchSize = 50;  // TODO: Load from config
    private final Set<String> languageList = Set.of("Java", "Kotlin", "Python", "Go", "Rust"); // TODO: Load from config

    // Semaphore for concurrency control (simple counter for prototype)
    private final int maxConcurrentTasks = 2; // TODO: Load from config
    private int currentRunningTasks = 0;

    // 1. Start ingestion task
    @PostMapping(value = "/ingestion/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskResponse> startIngestionTask(@RequestBody IngestionTaskRequest request) {
        logger.info("Received ingestion start request: start={}, end={}", request.getStartTime(), request.getEndTime());

        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime and endTime must be provided");
        }
        if (request.getEndTime().isBefore(request.getStartTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
        }

        if (currentRunningTasks >= maxConcurrentTasks) {
            logger.warn("Max concurrent ingestion tasks reached. Rejecting new task.");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Max concurrent ingestion tasks running");
        }

        String taskId = "task-" + Instant.now().toString().replace(":", "-") + "-" + UUID.randomUUID().toString().substring(0, 6);
        CommentIngestionTask task = new CommentIngestionTask(taskId, request.getStartTime(), request.getEndTime(), TaskStatus.INITIALIZED, 0, 0);
        ingestionTasks.put(taskId, task);

        currentRunningTasks++;
        processIngestionTaskAsync(task);

        logger.info("Created ingestion task with id {}", taskId);

        return ResponseEntity.ok(new IngestionTaskResponse(taskId, task.getStatus().toString().toLowerCase()));
    }

    @Async
    void processIngestionTaskAsync(CommentIngestionTask task) {
        CompletableFuture.runAsync(() -> {
            try {
                updateTaskStatus(task, TaskStatus.FETCHING_IDS);

                // TODO: Fetch item IDs from Firebase API using maxitem backward over time window
                // For prototype: simulate fetching 100 comment IDs
                List<String> fetchedCommentIds = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    fetchedCommentIds.add("comment-" + (1000 + i));
                }
                task.setCommentsTotalEstimate(fetchedCommentIds.size());
                updateTaskStatus(task, TaskStatus.FETCHING_COMMENTS);

                // Fetch comment content and filter deleted/dead (mocked)
                int fetchedCount = 0;
                List<Comment> batch = new ArrayList<>();
                for (String commentId : fetchedCommentIds) {
                    Comment comment = new Comment(commentId, "Sample comment text mentioning Java and Python.", CommentState.FETCHED, null);
                    comments.put(commentId, comment);

                    batch.add(comment);
                    fetchedCount++;
                    task.setCommentsFetched(fetchedCount);

                    if (batch.size() == batchSize || fetchedCount == fetchedCommentIds.size()) {
                        // Analyze batch via AI (mock)
                        analyzeCommentsBatch(batch);
                        batch.clear();
                    }
                }
                updateTaskStatus(task, TaskStatus.COMPLETED);
                logger.info("Completed ingestion task {}", task.getTaskId());
            } catch (Exception e) {
                updateTaskStatus(task, TaskStatus.FAILED);
                logger.error("Error processing ingestion task {}: {}", task.getTaskId(), e.getMessage(), e);
            } finally {
                currentRunningTasks--;
            }
        });
    }

    private void analyzeCommentsBatch(List<Comment> batch) {
        // TODO: Replace with real OpenAI API call and parsing
        logger.info("Analyzing batch of {} comments", batch.size());

        // Simulate AI response: each comment mentions random subset of languages once
        Random rnd = new Random();
        for (Comment comment : batch) {
            comment.setState(CommentState.ANALYZED);
            Set<String> mentioned = new HashSet<>();
            for (String lang : languageList) {
                if (rnd.nextBoolean()) { // Randomly mention language
                    mentioned.add(lang);
                }
            }
            LanguageMentions lm = new LanguageMentions(comment.getCommentId(), mentioned);
            languageMentionsStore.put(comment.getCommentId(), lm);

            // Aggregate counts per language daily/monthly/weekly - simplified and mocked here
            for (String lang : mentioned) {
                aggregates.merge(lang,
                        new LanguageMentionAggregate(lang, 1, AggregateState.INITIAL),
                        (oldAgg, newAgg) -> {
                            oldAgg.incrementCount(1);
                            oldAgg.setState(AggregateState.UPDATED);
                            return oldAgg;
                        });
            }
        }
    }

    private void updateTaskStatus(CommentIngestionTask task, TaskStatus newStatus) {
        task.setStatus(newStatus);
        logger.info("Task {} status updated to {}", task.getTaskId(), newStatus);
    }

    // 2. Get ingestion task status
    @GetMapping(value = "/ingestion/status/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskStatusResponse> getIngestionStatus(@PathVariable String taskId) {
        CommentIngestionTask task = ingestionTasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return ResponseEntity.ok(new IngestionTaskStatusResponse(
                task.getTaskId(),
                task.getStatus().toString().toLowerCase(),
                new Progress(task.getCommentsFetched(), task.getCommentsTotalEstimate())
        ));
    }

    // 3. Abort ingestion task
    @PostMapping(value = "/ingestion/abort/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskResponse> abortIngestionTask(@PathVariable String taskId) {
        CommentIngestionTask task = ingestionTasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        task.setStatus(TaskStatus.ABORTED);
        // Immediate cleanup: remove comments and language mentions related to this task (mocked)
        // TODO: Implement proper cleanup based on task association (here we clear all for demo)
        comments.clear();
        languageMentionsStore.clear();
        aggregates.clear();

        logger.info("Aborted and cleaned up ingestion task {}", taskId);
        return ResponseEntity.ok(new IngestionTaskResponse(taskId, task.getStatus().toString().toLowerCase()));
    }

    // 4. Query frequency for language
    @GetMapping(value = "/frequency/{language}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FrequencyResponse> getFrequency(
            @PathVariable String language,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        logger.info("Frequency query for language {} with range {} - {}", language, startDate, endDate);

        if (!languageList.contains(language)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language");
        }

        // Mocked aggregation response: return fixed data
        FrequencyData daily = new FrequencyData("2023-07-01", 12);
        FrequencyData weekly = new FrequencyData("2023-W27", 56);
        FrequencyData monthly = new FrequencyData("2023-07", 145);

        Map<String, List<FrequencyData>> freq = new HashMap<>();
        freq.put("daily", List.of(daily));
        freq.put("weekly", List.of(weekly));
        freq.put("monthly", List.of(monthly));

        FrequencyResponse response = new FrequencyResponse(language, freq);
        return ResponseEntity.ok(response);
    }

    // 5. Analyze comments batch (internal)
    @PostMapping(value = "/comments/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalyzeResponse> analyzeComments(@RequestBody AnalyzeRequest request) {
        logger.info("Received batch analyze request with {} comments", request.getComments().size());

        // TODO: Replace with real OpenAI API call and parsing
        List<AnalyzeResult> results = new ArrayList<>();
        for (AnalyzeComment c : request.getComments()) {
            // Mock: randomly mention languages
            Set<String> mentioned = new HashSet<>();
            for (String lang : languageList) {
                if (new Random().nextBoolean()) {
                    mentioned.add(lang);
                }
            }
            results.add(new AnalyzeResult(c.getCommentId(), new ArrayList<>(mentioned)));
        }

        return ResponseEntity.ok(new AnalyzeResponse(results));
    }

    // Basic error handling
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        logger.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    // --- DTOs and Entities ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class IngestionTaskRequest {
        private Instant startTime;
        private Instant endTime;
    }

    @Data
    @AllArgsConstructor
    static class IngestionTaskResponse {
        private String taskId;
        private String status;
    }

    @Data
    @AllArgsConstructor
    static class IngestionTaskStatusResponse {
        private String taskId;
        private String status;
        private Progress progress;
    }

    @Data
    @AllArgsConstructor
    static class Progress {
        private int commentsFetched;
        private int commentsTotalEstimate;
    }

    @Data
    @AllArgsConstructor
    static class CommentIngestionTask {
        private String taskId;
        private Instant startTime;
        private Instant endTime;
        private TaskStatus status;
        private int commentsFetched;
        private int commentsTotalEstimate;
    }

    enum TaskStatus {
        INITIALIZED,
        FETCHING_IDS,
        FETCHING_COMMENTS,
        COMPLETED,
        ABORTED,
        FAILED
    }

    @Data
    @AllArgsConstructor
    static class Comment {
        private String commentId;
        private String text;
        private CommentState state;
        private String errorMessage;
    }

    enum CommentState {
        FETCHED,
        PARSED,
        ANALYZED,
        FAILED
    }

    @Data
    @AllArgsConstructor
    static class LanguageMentions {
        private String commentId;
        private Set<String> languagesMentioned;
    }

    @Data
    @AllArgsConstructor
    static class LanguageMentionAggregate {
        private String language;
        private int count;
        private AggregateState state;

        void incrementCount(int delta) {
            this.count += delta;
        }
    }

    enum AggregateState {
        INITIAL,
        UPDATED,
        PERSISTED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class FrequencyResponse {
        private String language;
        private Map<String, List<FrequencyData>> frequency;
    }

    @Data
    @AllArgsConstructor
    static class FrequencyData {
        private String period;
        private int count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalyzeRequest {
        private List<AnalyzeComment> comments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalyzeComment {
        private String commentId;
        private String text;
    }

    @Data
    @AllArgsConstructor
    static class AnalyzeResponse {
        private List<AnalyzeResult> results;
    }

    @Data
    @AllArgsConstructor
    static class AnalyzeResult {
        private String commentId;
        private List<String> languagesMentioned;
    }
}
```