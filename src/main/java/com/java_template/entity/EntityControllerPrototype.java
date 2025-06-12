```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, CommentIngestionTask> ingestionTasks = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> ingestionTaskStatuses = new ConcurrentHashMap<>();

    private final Map<String, List<LanguageMentionAggregate>> aggregatesByLanguage = new ConcurrentHashMap<>();
    private final Map<String, List<LanguageMentionAggregate>> aggregatesAllLanguages = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private Map<String, List<String>> languageAliases;

    @PostConstruct
    public void init() {
        // TODO: Replace with ConfigurationProperties injection
        languageAliases = new HashMap<>();
        languageAliases.put("Go", Arrays.asList("go", "golang"));
        languageAliases.put("Python", Collections.singletonList("python"));
        languageAliases.put("Java", Collections.singletonList("java"));
        languageAliases.put("Kotlin", Collections.singletonList("kotlin"));
        languageAliases.put("Rust", Collections.singletonList("rust"));
        languageAliases.put("JavaScript", Arrays.asList("javascript", "js"));
        log.info("Loaded language aliases: {}", languageAliases);
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class CommentIngestionTask {
        private String taskId;
        private Instant startDate;
        private Instant endDate;
        private TaskState status;

        enum TaskState {
            initialized,
            fetching_ids,
            fetching_comments,
            completed,
            aborted,
            failed
        }
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
        private String timeSliceStart; // ISO date string representing day/week/month start
        private long count;
    }

    @Data
    static class CreateTaskRequest {
        private String startDate;
        private String endDate;
    }

    // --- API Endpoints ---

    /**
     * Create and initialize a new ingestion task.
     */
    @PostMapping("/ingestion/tasks")
    public ResponseEntity<CommentIngestionTask> createIngestionTask(@RequestBody CreateTaskRequest request) {
        Instant start, end;
        try {
            start = Instant.parse(request.getStartDate());
            end = Instant.parse(request.getEndDate());
        } catch (DateTimeParseException e) {
            log.error("Invalid date format in request: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO 8601 date format");
        }

        String taskId = UUID.randomUUID().toString();
        CommentIngestionTask task = new CommentIngestionTask(taskId, start, end, CommentIngestionTask.TaskState.initialized);
        ingestionTasks.put(taskId, task);
        ingestionTaskStatuses.put(taskId, new JobStatus("initialized", Instant.now()));

        log.info("Created ingestion task {} with window {} - {}", taskId, start, end);

        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /**
     * Process ingestion task: fetch IDs, comments, parse and analyze.
     * This is a fire-and-forget asynchronous prototype.
     */
    @PostMapping("/ingestion/tasks/{taskId}/process")
    public ResponseEntity<Map<String, Object>> processIngestionTask(@PathVariable String taskId) {
        CommentIngestionTask task = ingestionTasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }

        // Update status to fetching_ids
        task.setStatus(CommentIngestionTask.TaskState.fetching_ids);
        ingestionTaskStatuses.put(taskId, new JobStatus("fetching_ids", Instant.now()));
        log.info("Task {} moved to fetching_ids", taskId);

        // Fire-and-forget processing
        CompletableFuture.runAsync(() -> runIngestionTask(task));

        Map<String, Object> response = new HashMap<>();
        response.put("taskId", taskId);
        response.put("currentState", task.getStatus().name());
        return ResponseEntity.ok(response);
    }

    /**
     * Query language mention aggregates by single language.
     */
    @GetMapping("/aggregates")
    public ResponseEntity<Map<String, Object>> queryAggregates(
            @RequestParam String language,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {

        if (!languageAliases.containsKey(language)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language not supported");
        }
        String gran = granularity.toLowerCase(Locale.ROOT);
        if (!Set.of("day", "week", "month").contains(gran)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid granularity");
        }
        if (page < 1 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page and size must be positive");
        }

        List<LanguageMentionAggregate> aggregates = aggregatesByLanguage.getOrDefault(language, Collections.emptyList());
        // TODO: Filter by granularity if implemented, for prototype return all

        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, aggregates.size());
        List<LanguageMentionAggregate> pageData = (fromIndex >= aggregates.size()) ? Collections.emptyList() : aggregates.subList(fromIndex, toIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("language", language);
        response.put("granularity", gran);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (aggregates.size() + size - 1) / size);
        response.put("data", pageData);

        return ResponseEntity.ok(response);
    }

    /**
     * Query aggregates for all languages within a time window and granularity.
     */
    @GetMapping("/aggregates/all")
    public ResponseEntity<Map<String, Object>> queryAggregatesAll(
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {

        Instant start, end;
        try {
            start = Instant.parse(startDate);
            end = Instant.parse(endDate);
        } catch (DateTimeParseException e) {
            log.error("Invalid date format in aggregates/all request: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO 8601 date format");
        }

        String gran = granularity.toLowerCase(Locale.ROOT);
        if (!Set.of("day", "week", "month").contains(gran)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid granularity");
        }
        if (page < 1 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page and size must be positive");
        }

        // TODO: For prototype, ignoring time filtering and granularity filtering
        // Aggregate all languages data into a flat list for paging
        List<LanguageMentionAggregate> allAggregates = new ArrayList<>();
        aggregatesByLanguage.values().forEach(allAggregates::addAll);

        int fromIndex = (page - 1) * size;
        int toIndex = Math.min(fromIndex + size, allAggregates.size());
        List<LanguageMentionAggregate> pageData = (fromIndex >= allAggregates.size()) ? Collections.emptyList() : allAggregates.subList(fromIndex, toIndex);

        Map<String, Object> response = new HashMap<>();
        response.put("granularity", gran);
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (allAggregates.size() + size - 1) / size);
        response.put("data", pageData);

        return ResponseEntity.ok(response);
    }

    /**
     * List all ingestion tasks.
     */
    @GetMapping("/ingestion/tasks")
    public ResponseEntity<List<CommentIngestionTask>> listIngestionTasks() {
        return ResponseEntity.ok(new ArrayList<>(ingestionTasks.values()));
    }

    // --- INTERNAL PROCESSING LOGIC ---

    /**
     * Main ingestion task runner: fetches IDs, comments, parses, analyzes and updates aggregates.
     * Prototype: simplified logic with external calls to Hacker News Firebase API.
     */
    private void runIngestionTask(CommentIngestionTask task) {
        String taskId = task.getTaskId();
        try {
            log.info("Running ingestion task {}", taskId);

            // 1) Fetch max item id from Firebase
            task.setStatus(CommentIngestionTask.TaskState.fetching_ids);
            ingestionTaskStatuses.put(taskId, new JobStatus("fetching_ids", Instant.now()));

            Integer maxItemId = fetchMaxItemId();
            if (maxItemId == null) {
                throw new IllegalStateException("Failed to fetch max item id");
            }

            // 2) Build filtered comment IDs according to time window
            List<Integer> commentIds = fetchCommentIdsInTimeWindow(maxItemId, task.getStartDate(), task.getEndDate());
            log.info("Task {}: fetched {} comment IDs in window", taskId, commentIds.size());

            task.setStatus(CommentIngestionTask.TaskState.fetching_comments);
            ingestionTaskStatuses.put(taskId, new JobStatus("fetching_comments", Instant.now()));

            // 3) For each comment ID fetch, parse, detect languages and update aggregates
            for (Integer commentId : commentIds) {
                JsonNode commentJson = fetchCommentById(commentId);
                if (commentJson == null) {
                    log.warn("Comment {} not found or failed to fetch", commentId);
                    continue;
                }
                String text = extractCommentText(commentJson);
                Instant commentTime = extractCommentTime(commentJson);
                if (text == null || commentTime == null) continue;

                Set<String> detectedLanguages = detectLanguages(text);

                updateAggregates(detectedLanguages, commentTime);
            }

            task.setStatus(CommentIngestionTask.TaskState.completed);
            ingestionTaskStatuses.put(taskId, new JobStatus("completed", Instant.now()));
            log.info("Task {} completed", taskId);
        } catch (Exception e) {
            log.error("Task {} failed with error: {}", taskId, e.getMessage(), e);
            task.setStatus(CommentIngestionTask.TaskState.failed);
            ingestionTaskStatuses.put(taskId, new JobStatus("failed", Instant.now()));
        }
    }

    private Integer fetchMaxItemId() {
        try {
            String url = "https://hacker-news.firebaseio.com/v0/maxitem.json";
            String resp = restTemplate.getForObject(new URI(url), String.class);
            if (!StringUtils.hasText(resp)) return null;
            return Integer.valueOf(resp.trim());
        } catch (Exception e) {
            log.error("Failed to fetch maxitem: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Fetch comment IDs from maxItemId down to those matching the time window.
     * Prototype: fetch last max 5000 items, filter those of type "comment" and time window.
     * TODO: Implement proper time-to-ID bounding if available.
     */
    private List<Integer> fetchCommentIdsInTimeWindow(int maxItemId, Instant start, Instant end) {
        List<Integer> commentIds = new ArrayList<>();
        int limit = 5000; // prototype limit to last 5000 items

        for (int id = maxItemId; id > maxItemId - limit && id > 0; id--) {
            JsonNode itemJson = fetchItemById(id);
            if (itemJson == null) continue;
            String type = itemJson.path("type").asText("");
            if (!"comment".equalsIgnoreCase(type)) continue;
            long timeSec = itemJson.path("time").asLong(0);
            Instant commentTime = Instant.ofEpochSecond(timeSec);
            if (!commentTime.isBefore(start) && !commentTime.isAfter(end)) {
                commentIds.add(id);
            }
        }
        return commentIds;
    }

    private JsonNode fetchItemById(int id) {
        try {
            String url = String.format("https://hacker-news.firebaseio.com/v0/item/%d.json", id);
            String resp = restTemplate.getForObject(new URI(url), String.class);
            if (!StringUtils.hasText(resp)) return null;
            return objectMapper.readTree(resp);
        } catch (Exception e) {
            log.warn("Failed to fetch item {}: {}", id, e.getMessage());
            return null;
        }
    }

    private JsonNode fetchCommentById(int id) {
        return fetchItemById(id);
    }

    private String extractCommentText(JsonNode commentJson) {
        if (commentJson.has("text")) {
            return commentJson.get("text").asText("");
        }
        return null;
    }

    private Instant extractCommentTime(JsonNode commentJson) {
        long timeSec = commentJson.path("time").asLong(0);
        if (timeSec == 0) return null;
        return Instant.ofEpochSecond(timeSec);
    }

    /**
     * Detect languages in text using aliases (case-insensitive exact word or alias matching).
     */
    private Set<String> detectLanguages(String text) {
        Set<String> detected = new HashSet<>();
        if (text == null || text.isEmpty()) return detected;

        String lowerText = text.toLowerCase(Locale.ROOT);

        for (Map.Entry<String, List<String>> entry : languageAliases.entrySet()) {
            String canonical = entry.getKey();
            for (String alias : entry.getValue()) {
                String aliasLower = alias.toLowerCase(Locale.ROOT);
                // Exact word match using simple boundaries
                if (containsWord(lowerText, aliasLower)) {
                    detected.add(canonical);
                    break;
                }
            }
        }
        return detected;
    }

    private boolean containsWord(String text, String word) {
        // Simple word boundary check
        return Arrays.stream(text.split("\\W+")).anyMatch(w -> w.equalsIgnoreCase(word));
    }

    /**
     * Update aggregates map with detected languages and comment timestamp.
     * Prototype uses day granularity (ISO date string yyyy-MM-dd).
     */
    private void updateAggregates(Set<String> detectedLanguages, Instant commentTime) {
        if (detectedLanguages.isEmpty() || commentTime == null) return;
        String daySlice = commentTime.toString().substring(0, 10); // yyyy-MM-dd

        for (String lang : detectedLanguages) {
            aggregatesByLanguage.computeIfAbsent(lang, k -> Collections.synchronizedList(new ArrayList<>()));
            List<LanguageMentionAggregate> aggList = aggregatesByLanguage.get(lang);

            synchronized (aggList) {
                Optional<LanguageMentionAggregate> existing = aggList.stream()
                        .filter(a -> a.getTimeSliceStart().equals(daySlice))
                        .findFirst();
                if (existing.isPresent()) {
                    LanguageMentionAggregate a = existing.get();
                    a.setCount(a.getCount() + 1);
                } else {
                    aggList.add(new LanguageMentionAggregate(lang, daySlice, 1));
                }
            }
        }
    }

    // --- Basic error handler ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        log.error("Request failed: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}
```
