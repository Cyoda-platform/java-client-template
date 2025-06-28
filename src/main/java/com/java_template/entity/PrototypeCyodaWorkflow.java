package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-prototype")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final EntityService entityService;

    // Simulated persistent stores for supplementary entities of other entityModels
    private final Map<String, ObjectNode> comments = new ConcurrentHashMap<>();
    private final Map<String, ObjectNode> languageMentionsStore = new ConcurrentHashMap<>();
    private final Map<String, ObjectNode> aggregates = new ConcurrentHashMap<>();

    private final int batchSize = 50; // TODO: configurable
    private final Set<String> languageList = Set.of("Java", "Kotlin", "Python", "Go", "Rust"); // TODO: configurable

    private final int maxConcurrentTasks = 2; // TODO: configurable
    private int currentRunningTasks = 0;

    private static final String ENTITY_NAME = "prototype";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow function applied before persisting the ingestion task entity.
    // Contains all async ingestion processing logic.
    private final Function<Object, CompletableFuture<Object>> processPrototype = entityData -> {
        if (!(entityData instanceof ObjectNode)) {
            return CompletableFuture.completedFuture(entityData);
        }
        ObjectNode entity = (ObjectNode) entityData;

        if (!entity.has("taskId")) {
            // Not an ingestion task entity, no special processing
            return CompletableFuture.completedFuture(entity);
        }

        synchronized (this) {
            if (currentRunningTasks >= maxConcurrentTasks) {
                entity.put("status", "failed");
                entity.put("errorMessage", "Max concurrent ingestion tasks reached");
                return CompletableFuture.completedFuture(entity);
            }
            currentRunningTasks++;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Workflow: Starting ingestion processing for task {}", entity.get("taskId").asText());

                updateStatus(entity, "fetching_ids");
                List<String> fetchedCommentIds = fetchCommentIds(entity.get("startTime").asText(), entity.get("endTime").asText());
                entity.put("commentsTotalEstimate", fetchedCommentIds.size());

                updateStatus(entity, "fetching_comments");
                int fetchedCount = 0;
                List<ObjectNode> batch = new ArrayList<>();
                for (String commentId : fetchedCommentIds) {
                    ObjectNode comment = createCommentEntity(commentId);
                    comments.put(commentId, comment);
                    batch.add(comment);
                    fetchedCount++;
                    entity.put("commentsFetched", fetchedCount);

                    if (batch.size() == batchSize || fetchedCount == fetchedCommentIds.size()) {
                        analyzeCommentsBatch(batch);
                        batch.clear();
                    }
                }

                updateStatus(entity, "completed");
                logger.info("Workflow: Completed ingestion task {}", entity.get("taskId").asText());
            } catch (Exception ex) {
                updateStatus(entity, "failed");
                entity.put("errorMessage", ex.getMessage());
                logger.error("Workflow: Error processing ingestion task {}", entity.get("taskId").asText(), ex);
            } finally {
                synchronized (this) {
                    currentRunningTasks--;
                }
            }
            return entity;
        });
    };

    // Helper: update ingestion task status property and log
    private void updateStatus(ObjectNode entity, String status) {
        entity.put("status", status);
        logger.info("Workflow: Task {} status updated to {}", entity.get("taskId").asText(), status);
    }

    // Helper: simulate fetching comment IDs between given times
    private List<String> fetchCommentIds(String startTime, String endTime) {
        // Simulate with 100 fixed comment Ids for demo
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add("comment-" + (1000 + i));
        }
        return ids;
    }

    // Helper: create comment entity ObjectNode with initial state
    private ObjectNode createCommentEntity(String commentId) {
        ObjectNode comment = mapper.createObjectNode();
        comment.put("commentId", commentId);
        comment.put("text", "Sample comment text mentioning Java and Python.");
        comment.put("state", "fetched");
        return comment;
    }

    // Analyze batch of comments: mark analyzed, create language mentions, update aggregates
    private void analyzeCommentsBatch(List<ObjectNode> batch) {
        logger.info("Workflow: Analyzing batch of {} comments", batch.size());
        Random rnd = new Random();
        for (ObjectNode comment : batch) {
            comment.put("state", "analyzed");
            Set<String> mentioned = new HashSet<>();
            for (String lang : languageList) {
                if (rnd.nextBoolean()) {
                    mentioned.add(lang);
                }
            }
            ObjectNode lm = mapper.createObjectNode();
            lm.put("commentId", comment.get("commentId").asText());
            ArrayNode langArray = lm.putArray("languagesMentioned");
            mentioned.forEach(langArray::add);
            languageMentionsStore.put(comment.get("commentId").asText(), lm);

            for (String lang : mentioned) {
                aggregates.merge(lang, createInitialAggregate(lang), (oldAgg, newAgg) -> {
                    int oldCount = oldAgg.get("count").asInt(0);
                    oldAgg.put("count", oldCount + 1);
                    oldAgg.put("state", "updated");
                    return oldAgg;
                });
            }
        }
    }

    private ObjectNode createInitialAggregate(String lang) {
        ObjectNode agg = mapper.createObjectNode();
        agg.put("language", lang);
        agg.put("count", 1);
        agg.put("state", "initial");
        return agg;
    }

    // Controller endpoint: start ingestion task, persist with workflow that does async processing
    @PostMapping(value = "/ingestion/start", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskResponse> startIngestionTask(
            @RequestBody @Valid IngestionTaskRequest request) {
        logger.info("Received ingestion start request: start={}, end={}", request.getStartTime(), request.getEndTime());

        if (Instant.parse(request.getEndTime()).isBefore(Instant.parse(request.getStartTime()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endTime must be after startTime");
        }
        synchronized (this) {
            if (currentRunningTasks >= maxConcurrentTasks) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Max concurrent ingestion tasks running");
            }
        }

        ObjectNode task = mapper.createObjectNode();
        String taskId = "task-" + Instant.now().toString().replace(":", "-") + "-" +
                UUID.randomUUID().toString().substring(0, 6);
        task.put("taskId", taskId);
        task.put("startTime", request.getStartTime());
        task.put("endTime", request.getEndTime());
        task.put("status", "initialized");
        task.put("commentsFetched", 0);
        task.put("commentsTotalEstimate", 0);

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                task,
                processPrototype
        );

        idFuture.whenComplete((uuid, ex) -> {
            if (ex != null) {
                logger.error("Failed to persist ingestion task entity: {}", ex.getMessage(), ex);
            } else {
                logger.info("Ingestion task entity persisted with UUID: {}", uuid);
            }
        });

        return ResponseEntity.ok(new IngestionTaskResponse(taskId, "initialized"));
    }

    // Controller endpoint: get ingestion task status
    @GetMapping(value = "/ingestion/status/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskStatusResponse> getIngestionStatus(
            @PathVariable @NotBlank String taskId) {
        Optional<ObjectNode> optTask = findTaskById(taskId);
        if (optTask.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        ObjectNode task = optTask.get();
        return ResponseEntity.ok(new IngestionTaskStatusResponse(
                taskId,
                task.get("status").asText(),
                new Progress(task.get("commentsFetched").asInt(0), task.get("commentsTotalEstimate").asInt(0))
        ));
    }

    // Dummy lookup method: in real app query persistence layer or entityService
    private Optional<ObjectNode> findTaskById(String taskId) {
        // For demo, simulate not found. Implement as needed.
        return Optional.empty();
    }

    // Controller endpoint: abort ingestion task
    @PostMapping(value = "/ingestion/abort/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskResponse> abortIngestionTask(
            @PathVariable @NotBlank String taskId) {
        logger.info("Abort requested for ingestion task {}", taskId);
        // In real implementation: update ingestion task status to aborted and cleanup supplementary entities
        // Here just simulate response
        return ResponseEntity.ok(new IngestionTaskResponse(taskId, "aborted"));
    }

    // Controller endpoint: frequency query for language mentions
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

    // Controller endpoint: analyze batch of comments
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
}