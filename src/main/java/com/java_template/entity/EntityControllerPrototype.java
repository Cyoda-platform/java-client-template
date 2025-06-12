package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api")
public class EntityControllerPrototype {

    private final Map<String, CommentIngestionTask> ingestionTasks = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> ingestionTaskStatuses = new ConcurrentHashMap<>();
    private final Map<String, List<LanguageMentionAggregate>> aggregatesByLanguage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private Map<String, List<String>> languageAliases;

    @PostConstruct
    public void init() {
        languageAliases = new HashMap<>();
        languageAliases.put("Go", Arrays.asList("go", "golang"));
        languageAliases.put("Python", Collections.singletonList("python"));
        languageAliases.put("Java", Collections.singletonList("java"));
        languageAliases.put("Kotlin", Collections.singletonList("kotlin"));
        languageAliases.put("Rust", Collections.singletonList("rust"));
        log.info("Loaded language aliases: {}", languageAliases);
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

    @PostMapping("/ingestion/tasks")
    public ResponseEntity<CommentIngestionTask> createIngestionTask(@RequestBody @Valid CreateTaskRequest request) {
        Instant start, end;
        try {
            start = Instant.parse(request.getStartDate());
            end = Instant.parse(request.getEndDate());
        } catch (DateTimeParseException e) {
            log.error("Invalid date format: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO 8601 date format");
        }
        String taskId = UUID.randomUUID().toString();
        CommentIngestionTask task = new CommentIngestionTask(taskId, start, end, CommentIngestionTask.TaskState.initialized);
        ingestionTasks.put(taskId, task);
        ingestionTaskStatuses.put(taskId, new JobStatus("initialized", Instant.now()));
        log.info("Created ingestion task {} with window {} - {}", taskId, start, end);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @PostMapping("/ingestion/tasks/{taskId}/process")
    public ResponseEntity<Map<String, Object>> processIngestionTask(@PathVariable String taskId) {
        CommentIngestionTask task = ingestionTasks.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
        }
        task.setStatus(CommentIngestionTask.TaskState.fetching_ids);
        ingestionTaskStatuses.put(taskId, new JobStatus("fetching_ids", Instant.now()));
        log.info("Task {} moved to fetching_ids", taskId);
        CompletableFuture.runAsync(() -> runIngestionTask(task));
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
            @RequestParam(defaultValue = "100") @Min(1) int size) {
        if (!languageAliases.containsKey(language)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Language not supported");
        }
        List<LanguageMentionAggregate> aggregates = aggregatesByLanguage.getOrDefault(language, Collections.emptyList());
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
            @RequestParam(defaultValue = "100") @Min(1) int size) {
        Map<String, Object> resp = new HashMap<>();
        List<LanguageMentionAggregate> all = new ArrayList<>();
        aggregatesByLanguage.values().forEach(all::addAll);
        int from = (page - 1) * size;
        int to = Math.min(from + size, all.size());
        List<LanguageMentionAggregate> data = from >= all.size() ? Collections.emptyList() : all.subList(from, to);
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
    public ResponseEntity<List<CommentIngestionTask>> listIngestionTasks() {
        return ResponseEntity.ok(new ArrayList<>(ingestionTasks.values()));
    }

    private void runIngestionTask(CommentIngestionTask task) {
        String taskId = task.getTaskId();
        try {
            log.info("Running ingestion task {}", taskId);
            task.setStatus(CommentIngestionTask.TaskState.fetching_ids);
            ingestionTaskStatuses.put(taskId, new JobStatus("fetching_ids", Instant.now()));
            Integer maxItemId = fetchMaxItemId();
            List<Integer> commentIds = fetchCommentIdsInTimeWindow(maxItemId, task.getStartDate(), task.getEndDate());
            task.setStatus(CommentIngestionTask.TaskState.fetching_comments);
            ingestionTaskStatuses.put(taskId, new JobStatus("fetching_comments", Instant.now()));
            for (Integer commentId : commentIds) {
                JsonNode commentJson = fetchItemById(commentId);
                if (commentJson == null) continue;
                String text = commentJson.path("text").asText("");
                Instant commentTime = Instant.ofEpochSecond(commentJson.path("time").asLong(0));
                Set<String> detected = detectLanguages(text);
                updateAggregates(detected, commentTime);
            }
            task.setStatus(CommentIngestionTask.TaskState.completed);
            ingestionTaskStatuses.put(taskId, new JobStatus("completed", Instant.now()));
            log.info("Task {} completed", taskId);
        } catch (Exception e) {
            log.error("Task {} failed: {}", taskId, e.getMessage(), e);
            task.setStatus(CommentIngestionTask.TaskState.failed);
            ingestionTaskStatuses.put(taskId, new JobStatus("failed", Instant.now()));
        }
    }

    private Integer fetchMaxItemId() {
        try {
            String url = "https://hacker-news.firebaseio.com/v0/maxitem.json";
            String resp = restTemplate.getForObject(new URI(url), String.class);
            return StringUtils.hasText(resp) ? Integer.valueOf(resp.trim()) : null;
        } catch (Exception e) {
            log.error("Failed to fetch maxitem: {}", e.getMessage());
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
            log.warn("Failed to fetch item {}: {}", id, e.getMessage());
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

    private void updateAggregates(Set<String> langs, Instant time) {
        if (langs.isEmpty()) return;
        String day = time.toString().substring(0, 10);
        for (String lang : langs) {
            aggregatesByLanguage.computeIfAbsent(lang, k -> Collections.synchronizedList(new ArrayList<>()));
            List<LanguageMentionAggregate> list = aggregatesByLanguage.get(lang);
            synchronized (list) {
                list.stream()
                    .filter(a -> a.getTimeSliceStart().equals(day))
                    .findFirst()
                    .ifPresentOrElse(
                        a -> a.setCount(a.getCount() + 1),
                        () -> list.add(new LanguageMentionAggregate(lang, day, 1))
                    );
            }
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        log.error("Request failed: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}