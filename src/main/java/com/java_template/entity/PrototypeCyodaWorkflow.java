Certainly! The best practice here is to move all async logic and processing related to the entity into the workflow function `processPrototype`. This will keep controllers lean and delegate business logic to the workflow layer, which executes right before persistence asynchronously.

---

### What to move into `processPrototype`:

- The ingestion task processing (`processIngestionTaskAsync`) logic: fetching comment IDs, fetching comments, analyzing comments, updating aggregates, updating task status, etc.

- Any batch processing, language mention extraction, and aggregate updates.

---

### How to do this:

- The controller endpoint only creates the initial task entity and calls `entityService.addItem` with the workflow function.

- The workflow function receives the entity as an `ObjectNode`, modifies its state (e.g. status, counters) as needed.

- The workflow function can retrieve or add supplementary entities of other `entityModel`s (e.g. comments, aggregates).

- The workflow function returns the updated entity, which will then be persisted by `entityService`.

---

### Important constraints:

- Do **not** add/update/delete entities of the same entity model inside the workflow (infinite recursion).

- The workflow function must be asynchronous, returning `CompletableFuture<Object>`.

---

Below is the updated full Java code illustrating this approach.  
I replaced the entire async ingestion task processing inside the workflow `processPrototype` and simplified controller code accordingly.

---

```java
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    // For demo purposes: storage of supplementary entities keyed by id
    private final Map<String, ObjectNode> comments = new ConcurrentHashMap<>();
    private final Map<String, ObjectNode> languageMentionsStore = new ConcurrentHashMap<>();
    private final Map<String, ObjectNode> aggregates = new ConcurrentHashMap<>();

    private final int batchSize = 50;  // TODO: Load from config
    private final Set<String> languageList = Set.of("Java", "Kotlin", "Python", "Go", "Rust"); // TODO: Load from config

    private final int maxConcurrentTasks = 2; // TODO: Load from config
    private int currentRunningTasks = 0;

    private static final String ENTITY_NAME = "prototype";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function applied before persisting the ingestion task entity.
     * Moves entire ingestion processing logic here as async workflow.
     * Must NOT add/update/delete entities of the same entityModel here.
     */
    private final Function<Object, CompletableFuture<Object>> processPrototype = entityData -> {
        if (!(entityData instanceof ObjectNode)) {
            return CompletableFuture.completedFuture(entityData);
        }
        ObjectNode entity = (ObjectNode) entityData;

        // Process only if it's an ingestion task entity (basic check)
        if (!entity.has("taskId")) {
            return CompletableFuture.completedFuture(entity);
        }

        // Limit concurrent tasks
        synchronized (this) {
            if (currentRunningTasks >= maxConcurrentTasks) {
                entity.put("status", "failed");
                entity.put("errorMessage", "Max concurrent ingestion tasks reached");
                return CompletableFuture.completedFuture(entity);
            }
            currentRunningTasks++;
        }

        // Run ingestion processing asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Workflow: Starting ingestion processing for task {}", entity.get("taskId").asText());

                updateStatus(entity, "fetching_ids");
                // Simulate fetching comment IDs
                List<String> fetchedCommentIds = new ArrayList<>();
                for (int i = 0; i < 100; i++) {
                    fetchedCommentIds.add("comment-" + (1000 + i));
                }
                entity.put("commentsTotalEstimate", fetchedCommentIds.size());

                updateStatus(entity, "fetching_comments");
                int fetchedCount = 0;
                List<ObjectNode> batch = new ArrayList<>();
                for (String commentId : fetchedCommentIds) {
                    // Create comment entity as ObjectNode (different entityModel)
                    ObjectNode comment = mapper.createObjectNode();
                    comment.put("commentId", commentId);
                    comment.put("text", "Sample comment text mentioning Java and Python.");
                    comment.put("state", "fetched");
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

    private void updateStatus(ObjectNode entity, String status) {
        entity.put("status", status);
        logger.info("Workflow: Task {} status updated to {}", entity.get("taskId").asText(), status);
    }

    /**
     * Analyze comments batch: update comment state, language mentions, aggregates.
     * All supplementary entities are other entityModels, allowed to add/update here.
     */
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
            // Store language mentions entity (different entityModel)
            ObjectNode lm = mapper.createObjectNode();
            lm.put("commentId", comment.get("commentId").asText());
            ArrayNode langArray = lm.putArray("languagesMentioned");
            mentioned.forEach(langArray::add);
            languageMentionsStore.put(comment.get("commentId").asText(), lm);

            // Update aggregates per language
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

    // === Controller endpoints simplified ===

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
        // Create ingestion task entity as ObjectNode
        ObjectNode task = mapper.createObjectNode();
        String taskId = "task-" + Instant.now().toString().replace(":", "-") + "-" +
                UUID.randomUUID().toString().substring(0, 6);
        task.put("taskId", taskId);
        task.put("startTime", request.getStartTime());
        task.put("endTime", request.getEndTime());
        task.put("status", "initialized");
        task.put("commentsFetched", 0);
        task.put("commentsTotalEstimate", 0);

        // Persist task entity with workflow function applied
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

    @GetMapping(value = "/ingestion/status/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskStatusResponse> getIngestionStatus(
            @PathVariable @NotBlank String taskId) {
        // Since tasks are stored inside workflow function state only, simulate fetching task
        // For demonstration, just return dummy data or throw NOT_FOUND if unknown
        // In real app, you'd query persisted entity by taskId with entityService or DB
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

    // Dummy method for demonstration - in real app, query DB or entityService
    private Optional<ObjectNode> findTaskById(String taskId) {
        // For demo, check if taskId matches some criteria or return empty
        // Here we simulate not found always
        return Optional.empty();
    }

    @PostMapping(value = "/ingestion/abort/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionTaskResponse> abortIngestionTask(
            @PathVariable @NotBlank String taskId) {
        // For demo, just log and respond aborted
        logger.info("Abort requested for ingestion task {}", taskId);
        // Real implementation should update task status to aborted and cleanup supplementary entities
        return ResponseEntity.ok(new IngestionTaskResponse(taskId, "aborted"));
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

    // === DTOs simplified ===

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
```

---

### Key points:

- The `processPrototype` workflow function now contains **all** async ingestion task logic, including fetching comments, analyzing, updating status, etc.

- The controller only creates the ingestion task entity and calls `entityService.addItem` with the workflow function.

- The workflow function modifies the same entity (`ObjectNode`), updating status and counters.

- Supplementary entities (comments, language mentions, aggregates) are created/updated inside the workflow function as separate `ObjectNode`s stored in different maps (simulating other entityModels).

- Workflow returns the updated entity which `entityService` persists.

- This pattern fully **frees the controller from async logic** and makes the code more robust, testable, and maintainable.

---

If you need me to help move other async tasks or endpoints similarly to new workflow functions, just ask!