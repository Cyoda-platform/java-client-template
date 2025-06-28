package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private final Map<String, CommentIngestionTask> ingestionTasks = new ConcurrentHashMap<>();
    private final Map<String, Comment> comments = new ConcurrentHashMap<>();
    private final Map<String, LanguageMentions> languageMentionsStore = new ConcurrentHashMap<>();
    private final Map<String, LanguageMentionAggregate> aggregates = new ConcurrentHashMap<>();

    private final int batchSize = 50;  // TODO: Load from config
    private final Set<String> languageList = Set.of("Java", "Kotlin", "Python", "Go", "Rust"); // TODO: Load from config

    private final int maxConcurrentTasks = 2; // TODO: Load from config
    private int currentRunningTasks = 0;

    private static final String ENTITY_NAME = "prototype";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(value = "/ingestion/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskResponse> startIngestionTask(
            @RequestBody @Valid IngestionTaskRequest request) {
        logger.info("Received ingestion start request: start={}, end={}", request.getStartTime(), request.getEndTime());
        if (Instant.parse(request.getEndTime()).isBefore(Instant.parse(request.getStartTime()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
        }
        if (currentRunningTasks >= maxConcurrentTasks) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Max concurrent ingestion tasks running");
        }
        String taskId = "task-" + Instant.now().toString().replace(":", "-") + "-" +
                UUID.randomUUID().toString().substring(0, 6);
        CommentIngestionTask task = new CommentIngestionTask(taskId, request.getStartTime(),
                request.getEndTime(), TaskStatus.INITIALIZED, 0, 0);
        ingestionTasks.put(taskId, task);
        currentRunningTasks++;
        processIngestionTaskAsync(task);
        logger.info("Created ingestion task with id {}", taskId);
        return ResponseEntity.ok(new IngestionTaskResponse(taskId, task.getStatus().name().toLowerCase()));
    }

    @Async
    void processIngestionTaskAsync(CommentIngestionTask task) {
        CompletableFuture.runAsync(() -> {
            try {
                updateTaskStatus(task, TaskStatus.FETCHING_IDS);
                List<String> fetchedCommentIds = new ArrayList<>();
                for (int i = 0; i < 100; i++) fetchedCommentIds.add("comment-" + (1000 + i));
                task.setCommentsTotalEstimate(fetchedCommentIds.size());
                updateTaskStatus(task, TaskStatus.FETCHING_COMMENTS);
                int fetchedCount = 0;
                List<Comment> batch = new ArrayList<>();
                for (String commentId : fetchedCommentIds) {
                    Comment comment = new Comment(commentId,
                            "Sample comment text mentioning Java and Python.", CommentState.FETCHED, null);
                    comments.put(commentId, comment);
                    batch.add(comment);
                    fetchedCount++;
                    task.setCommentsFetched(fetchedCount);
                    if (batch.size() == batchSize || fetchedCount == fetchedCommentIds.size()) {
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
        logger.info("Analyzing batch of {} comments", batch.size());
        Random rnd = new Random();
        for (Comment comment : batch) {
            comment.setState(CommentState.ANALYZED);
            Set<String> mentioned = new HashSet<>();
            for (String lang : languageList) if (rnd.nextBoolean()) mentioned.add(lang);
            LanguageMentions lm = new LanguageMentions(comment.getCommentId(), mentioned);
            languageMentionsStore.put(comment.getCommentId(), lm);
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

    @GetMapping(value = "/ingestion/status/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskStatusResponse> getIngestionStatus(
            @PathVariable @NotBlank String taskId) {
        CommentIngestionTask task = ingestionTasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        return ResponseEntity.ok(new IngestionTaskStatusResponse(
                task.getTaskId(),
                task.getStatus().name().toLowerCase(),
                new Progress(task.getCommentsFetched(), task.getCommentsTotalEstimate())
        ));
    }

    @PostMapping(value = "/ingestion/abort/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskResponse> abortIngestionTask(
            @PathVariable @NotBlank String taskId) {
        CommentIngestionTask task = ingestionTasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        task.setStatus(TaskStatus.ABORTED);
        comments.clear();
        languageMentionsStore.clear();
        aggregates.clear();
        logger.info("Aborted and cleaned up ingestion task {}", taskId);
        return ResponseEntity.ok(new IngestionTaskResponse(taskId, task.getStatus().name().toLowerCase()));
    }

    @GetMapping(value = "/frequency/{language}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FrequencyResponse> getFrequency(
            @PathVariable @NotBlank String language,
            @RequestParam(required = false) @Pattern(regexp = "\\d{4}-[01]\\d-[0-3]\\d") String startDate,
            @RequestParam(required = false) @Pattern(regexp = "\\d{4}-[01]\\d-[0-3]\\d") String endDate) {
        logger.info("Frequency query for {} range {} - {}", language, startDate, endDate);
        if (!languageList.contains(language)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported language");
        }
        FrequencyData daily = new FrequencyData("2023-07-01", 12);
        FrequencyData weekly = new FrequencyData("2023-W27", 56);
        FrequencyData monthly = new FrequencyData("2023-07", 145);
        Map<String, List<FrequencyData>> freq = new HashMap<>();
        freq.put("daily", List.of(daily));
        freq.put("weekly", List.of(weekly));
        freq.put("monthly", List.of(monthly));
        return ResponseEntity.ok(new FrequencyResponse(language, freq));
    }

    @PostMapping(value = "/comments/analyze", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalyzeResponse> analyzeComments(
            @RequestBody @Valid AnalyzeRequest request) {
        logger.info("Received batch analyze request with {} comments", request.getComments().size());
        List<AnalyzeResult> results = new ArrayList<>();
        Random rnd = new Random();
        for (AnalyzeComment c : request.getComments()) {
            Set<String> mentioned = new HashSet<>();
            for (String lang : languageList) if (rnd.nextBoolean()) mentioned.add(lang);
            results.add(new AnalyzeResult(c.getCommentId(), new ArrayList<>(mentioned)));
        }
        return ResponseEntity.ok(new AnalyzeResponse(results));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        );
        logger.error("Handled error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class IngestionTaskRequest {
        @NotBlank
        @Pattern(regexp = "\\d{4}-[01]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\dZ")
        private String startTime;
        @NotBlank
        @Pattern(regexp = "\\d{4}-[01]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\dZ")
        private String endTime;
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
        private String startTime;
        private String endTime;
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
        FETCHED, PARSED, ANALYZED, FAILED
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
        void incrementCount(int delta) { this.count += delta; }
    }

    enum AggregateState { INITIAL, UPDATED, PERSISTED }

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
    static class AnalyzeRequest {
        @Size(min = 1)
        @Valid
        private List<AnalyzeComment> comments;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AnalyzeComment {
        @NotBlank
        private String commentId;
        @NotBlank
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