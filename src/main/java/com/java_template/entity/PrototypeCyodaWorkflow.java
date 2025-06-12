```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private Map<String, List<String>> languageAliases;

    // Removed local in-memory maps for ingestionTasks, statuses, aggregates

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        languageAliases = new HashMap<>();
        languageAliases.put("Go", Arrays.asList("go", "golang"));
        languageAliases.put("Python", Collections.singletonList("python"));
        languageAliases.put("Java", Collections.singletonList("java"));
        languageAliases.put("Kotlin", Collections.singletonList("kotlin"));
        languageAliases.put("Rust", Collections.singletonList("rust"));
        logger.info("Loaded language aliases: {}", languageAliases);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CommentIngestionTask {
        private String taskId;
        private Instant startDate;
        private Instant endDate;
        private TaskState status;
        enum TaskState { initialized, fetching_ids, fetching_comments, completed, aborted, failed }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class LanguageMentionAggregate {
        private String language;
        private String timeSliceStart;
        private long count;
    }

    @Data
    static class CreateTaskRequest {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", message = "startDate must be ISO 8601 UTC")
        private String startDate;
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", message = "endDate must be ISO 8601 UTC")
        private String endDate;
    }

    /**
     * Workflow function for CommentIngestionTask entity.
     * This function receives the entity, can modify its state asynchronously before persistence,
     * then returns the (possibly) modified entity.
     */
    private Function<CommentIngestionTask, CommentIngestionTask> processCommentIngestionTask = (task) -> {
        // Here you can modify the task before it is persisted.
        // For example, set the status to initialized if not set.
        if (task.getStatus() == null) {
            task.setStatus(CommentIngestionTask.TaskState.initialized);
        }
        // You can add more logic here if needed.
        return task;
    };

    @PostMapping("/ingestion/tasks")
    public ResponseEntity<CommentIngestionTask> createIngestionTask(@RequestBody @Valid CreateTaskRequest request) throws ExecutionException, InterruptedException {
        Instant start, end;
        try {
            start = Instant.parse(request.getStartDate());
            end = Instant.parse(request.getEndDate());
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO 8601 date format");
        }

        CommentIngestionTask task = new CommentIngestionTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setStartDate(start);
        task.setEndDate(end);
        task.setStatus(CommentIngestionTask.TaskState.initialized);

        // Save task via entityService with workflow function applied
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "CommentIngestionTask",
                ENTITY_VERSION,
                task,
                processCommentIngestionTask
        );
        UUID techId = idFuture.get();

        // Set technicalId as taskId for consistency, although original uses String taskId, here we keep both
        task.setTaskId(techId.toString());

        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @PostMapping("/ingestion/tasks/{taskId}/process")
    public ResponseEntity<Map<String, Object>> processIngestionTask(@PathVariable String taskId) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(taskId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid taskId format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("CommentIngestionTask", ENTITY_VERSION, technicalId);
        ObjectNode taskNode = itemFuture.get();
        if (taskNode == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }

        // Deserialize to CommentIngestionTask
        CommentIngestionTask task = objectMapper.convertValue(taskNode, CommentIngestionTask.class);

        // Update status to fetching_ids
        task.setStatus(CommentIngestionTask.TaskState.fetching_ids);
        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem("CommentIngestionTask", ENTITY_VERSION, technicalId, task);
        updatedIdFuture.get();

        logger.info("Task {} moved to fetching_ids", taskId);

        CompletableFuture.runAsync(() -> runIngestionTask(task, technicalId));

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("currentState", task.getStatus().name());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/aggregates")
    public ResponseEntity<Map<String, Object>> queryAggregates(
            @RequestParam @NotBlank String language,
            @RequestParam(defaultValue = "day") @Pattern(regexp = "day|week|month") String granularity,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) int size) throws ExecutionException, InterruptedException {

        if (!languageAliases.containsKey(language)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language not supported");
        }

        // Retrieve all aggregates for language
        String condition = String.format("{\"language\":\"%s\"}", language);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("LanguageMentionAggregate", ENTITY_VERSION, condition);
        ArrayNode aggregatesArray = filteredItemsFuture.get();

        List<LanguageMentionAggregate> aggregates = new ArrayList<>();
        for (JsonNode node : aggregatesArray) {
            LanguageMentionAggregate agg = objectMapper.convertValue(node, LanguageMentionAggregate.class);
            aggregates.add(agg);
        }

        int from = (page - 1) * size;
        int to = Math.min(from + size, aggregates.size());
        List<LanguageMentionAggregate> data = from >= aggregates.size() ? Collections.emptyList() : aggregates.subList(from, to);

        Map<String, Object> resp = new HashMap<>();
        resp.put("language", language);
        resp.put("granularity", granularity);
        resp.put("page", page);
        resp.put("size", size);
        resp.put("totalPages", (aggregates.size() + size - 1) / size);
        resp.put("data", data);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/aggregates/all")
    public ResponseEntity<Map<String, Object>> queryAggregatesAll(
            @RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$") String startDate,
            @RequestParam @NotBlank @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$") String endDate,
            @RequestParam(defaultValue = "day") @Pattern(regexp = "day|week|month") String granularity,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "100") @Min(1) int size) throws ExecutionException, InterruptedException {

        // Retrieve all aggregates without filter
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("LanguageMentionAggregate", ENTITY_VERSION);
        ArrayNode allItems = itemsFuture.get();

        List<LanguageMentionAggregate> all = new ArrayList<>();
        for (JsonNode node : allItems) {
            LanguageMentionAggregate agg = objectMapper.convertValue(node, LanguageMentionAggregate.class);
            all.add(agg);
        }

        int from = (page - 1) * size;
        int to = Math.min(from + size, all.size());
        List<LanguageMentionAggregate> data = from >= all.size() ? Collections.emptyList() : all.subList(from, to);

        Map<String, Object> resp = new HashMap<>();
        resp.put("granularity", granularity);
        resp.put("startDate", startDate);
        resp.put("endDate", endDate);
        resp.put("page", page);
        resp.put("size", size);
        resp.put("totalPages", (all.size() + size - 1) / size);
        resp.put("data", data);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/ingestion/tasks")
    public ResponseEntity<List<CommentIngestionTask>> listIngestionTasks() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("CommentIngestionTask", ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        List<CommentIngestionTask> tasks = new ArrayList<>();
        for (JsonNode node : items) {
            CommentIngestionTask task = objectMapper.convertValue(node, CommentIngestionTask.class);
            tasks.add(task);
        }
        return ResponseEntity.ok(tasks);
    }

    private void runIngestionTask(CommentIngestionTask task, UUID technicalId) {
        String taskId = task.getTaskId();
        try {
            logger.info("Running ingestion task {}", taskId);
            task.setStatus(CommentIngestionTask.TaskState.fetching_ids);
            entityService.updateItem("CommentIngestionTask", ENTITY_VERSION, technicalId, task);

            Integer maxItemId = fetchMaxItemId();
            if (maxItemId == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch max item id");
            }

            List<Integer> commentIds = fetchCommentIdsInTimeWindow(maxItemId, task.getStartDate(), task.getEndDate());

            task.setStatus(CommentIngestionTask.TaskState.fetching_comments);
            entityService.updateItem("CommentIngestionTask", ENTITY_VERSION, technicalId, task);

            for (Integer commentId : commentIds) {
                JsonNode commentJson = fetchItemById(commentId);
                if (commentJson == null) continue;
                String text = commentJson.path("text").asText("");
                Instant commentTime = Instant.ofEpochSecond(commentJson.path("time").asLong(0));
                Set<String> detected = detectLanguages(text);
                updateAggregates(detected, commentTime);
            }

            task.setStatus(CommentIngestionTask.TaskState.completed);
            entityService.updateItem("CommentIngestionTask", ENTITY_VERSION, technicalId, task);

            logger.info("Task {} completed", taskId);
        } catch (Exception e) {
            logger.error("Task {} failed: {}", taskId, e.getMessage(), e);
            try {
                task.setStatus(CommentIngestionTask.TaskState.failed);
                entityService.updateItem("CommentIngestionTask", ENTITY_VERSION, technicalId, task);
            } catch (Exception ex) {
                logger.error("Failed to update task status to failed: {}", ex.getMessage(), ex);
            }
        }
    }

    private Integer fetchMaxItemId() {
        try {
            String url = "https://hacker-news.firebaseio.com/v0/maxitem.json";
            String resp = restTemplate.getForObject(new URI(url), String.class);
            return StringUtils.hasText(resp) ? Integer.valueOf(resp.trim()) : null;
        } catch (Exception e) {
            logger.error("Failed to fetch maxitem: {}", e.getMessage());
            return null;
        }
    }

    private List<Integer> fetchCommentIdsInTimeWindow(int maxItemId, Instant start, Instant end) {
        List<Integer> ids = new ArrayList<>();
        int limit = 5000;
        for (int id = maxItemId; id > maxItemId - limit && id > 0; id--) {
            JsonNode item = fetchItemById(id);
            if (item == null) continue;
            if (!"comment".equalsIgnoreCase(item.path("type").asText(""))) continue;
            Instant t = Instant.ofEpochSecond(item.path("time").asLong(0));
            if (!t.isBefore(start) && !t.isAfter(end)) ids.add(id);
        }
        return ids;
    }

    private JsonNode fetchItemById(int id) {
        try {
            String url = String.format("https://hacker-news.firebaseio.com/v0/item/%d.json", id);
            String resp = restTemplate.getForObject(new URI(url), String.class);
            return StringUtils.hasText(resp) ? objectMapper.readTree(resp) : null;
        } catch (Exception e) {
            logger.warn("Failed to fetch item {}: {}", id, e.getMessage());
            return null;
        }
    }

    private Set<String> detectLanguages(String text) {
        Set<String> detected = new HashSet<>();
        if (text == null) return detected;
        String lower = text.toLowerCase(Locale.ROOT);
        for (var entry : languageAliases.entrySet()) {
            for (String alias : entry.getValue()) {
                if (Arrays.stream(lower.split("\\W+")).anyMatch(w -> w.equalsIgnoreCase(alias))) {
                    detected.add(entry.getKey());
                    break;
                }
            }
        }
        return detected;
    }

    private void updateAggregates(Set<String> langs, Instant time) throws ExecutionException, InterruptedException {
        if (langs.isEmpty()) return;
        String day = time.toString().substring(0, 10);
        for (String lang : langs) {
            // Try to find aggregate by language and day
            String condition = String.format("{\"language\":\"%s\",\"timeSliceStart\":\"%s\"}", lang, day);
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("LanguageMentionAggregate", ENTITY_VERSION, condition);
            ArrayNode filtered = filteredItemsFuture.get();
            if (filtered.size() > 0) {
                // update existing aggregate
                JsonNode existingNode = filtered.get(0);
                LanguageMentionAggregate agg = objectMapper.convertValue(existingNode, LanguageMentionAggregate.class);
                agg.setCount(agg.getCount() + 1);
                UUID technicalId = UUID.fromString(existingNode.get("technicalId").asText());
                entityService.updateItem("LanguageMentionAggregate", ENTITY_VERSION, technicalId, agg);
            } else {
                // create new aggregate with workflow function applied
                LanguageMentionAggregate newAgg = new LanguageMentionAggregate(lang, day, 1);
                // Define workflow function for LanguageMentionAggregate
                Function<LanguageMentionAggregate, LanguageMentionAggregate> processLanguageMentionAggregate = (aggregate) -> {
                    // You can modify the aggregate here if needed before persistence
                    return aggregate;
                };
                entityService.addItem("LanguageMentionAggregate", ENTITY_VERSION, newAgg, processLanguageMentionAggregate);
            }
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        logger.error("Request failed: {}", ex.getStatusCode().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}
```

---

### Explanation of changes:
- **Workflow function `processCommentIngestionTask`**:
  - Added a `Function<CommentIngestionTask, CommentIngestionTask>` named `processCommentIngestionTask`.
  - This function is passed as the last argument to `entityService.addItem` in `createIngestionTask`.
  - This function can mutate or prepare the entity before persistence.

- **Updated `entityService.addItem` calls**:
  - Now pass the workflow function as the last argument.
  - For `CommentIngestionTask`, used `processCommentIngestionTask`.
  - For `LanguageMentionAggregate` inside `updateAggregates`, defined and passed a workflow function inline.

- **Imports and types**:
  - Added `import java.util.function.Function;` to support passing functions as workflow parameter.

This fully respects the requirement that the workflow function is a parameter to `addItem`, named as `process{entity_name}`, taking the entity as input and returning it (modified or not).