package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.annotation.PostConstruct;
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
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/cyodaentity")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private Map<String, List<String>> languageAliases;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper; // Injected via constructor
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
    static class CreateTaskRequest {
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", message = "startDate must be ISO 8601 UTC")
        private String startDate;
        @NotBlank
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$", message = "endDate must be ISO 8601 UTC")
        private String endDate;
    }

    @PostMapping("/ingestion/tasks")
    public ResponseEntity<ObjectNode> createIngestionTask(@RequestBody @Valid CreateTaskRequest request) throws ExecutionException, InterruptedException {
        Instant start, end;
        try {
            start = Instant.parse(request.getStartDate());
            end = Instant.parse(request.getEndDate());
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ISO 8601 date format");
        }

        ObjectNode taskNode = objectMapper.createObjectNode();
        taskNode.put("taskId", UUID.randomUUID().toString());
        taskNode.put("startDate", start.toString());
        taskNode.put("endDate", end.toString());
        taskNode.put("status", "initialized");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "CommentIngestionTask",
                ENTITY_VERSION,
                taskNode
        );
        UUID technicalId = idFuture.get();

        taskNode.put("technicalId", technicalId.toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(taskNode);
    }

    // Trigger processing again by updating the entity
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
        CompletableFuture<UUID> updateFuture = entityService.updateItem(
                "CommentIngestionTask",
                ENTITY_VERSION,
                technicalId,
                taskNode
        );
        updateFuture.get();

        Map<String, Object> resp = new HashMap<>();
        resp.put("taskId", taskId);
        resp.put("currentState", taskNode.path("status").asText("unknown"));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/ingestion/tasks")
    public ResponseEntity<ArrayNode> listIngestionTasks() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("CommentIngestionTask", ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        return ResponseEntity.ok(items);
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

        String condition = String.format("{\"language\":\"%s\"}", language);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("LanguageMentionAggregate", ENTITY_VERSION, condition);
        ArrayNode aggregatesArray = filteredItemsFuture.get();

        List<JsonNode> aggregates = new ArrayList<>();
        aggregatesArray.forEach(aggregates::add);

        int from = (page - 1) * size;
        int to = Math.min(from + size, aggregates.size());
        List<JsonNode> data = from >= aggregates.size() ? Collections.emptyList() : aggregates.subList(from, to);

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

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("LanguageMentionAggregate", ENTITY_VERSION);
        ArrayNode allItems = itemsFuture.get();

        List<JsonNode> all = new ArrayList<>();
        allItems.forEach(all::add);

        int from = (page - 1) * size;
        int to = Math.min(from + size, all.size());
        List<JsonNode> data = from >= all.size() ? Collections.emptyList() : all.subList(from, to);

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

    // Helper methods

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
            Instant t;
            try {
                t = Instant.ofEpochSecond(item.path("time").asLong(0));
            } catch (Exception e) {
                continue;
            }
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
            logger.warn("Failed to fetch item %d: %s", id, e.getMessage());
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

    // This method updates aggregates asynchronously and uses entityService on different entityModel
    private void updateAggregates(Set<String> langs, Instant time) throws ExecutionException, InterruptedException {
        if (langs.isEmpty()) return;
        String day = time.toString().substring(0, 10);
        for (String lang : langs) {
            String condition = String.format("{\"language\":\"%s\",\"timeSliceStart\":\"%s\"}", lang, day);
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("LanguageMentionAggregate", ENTITY_VERSION, condition);
            ArrayNode filtered = filteredItemsFuture.get();
            if (filtered.size() > 0) {
                JsonNode existingNode = filtered.get(0);
                UUID technicalId = UUID.fromString(existingNode.get("technicalId").asText());
                long count = existingNode.get("count").asLong(0) + 1;
                ObjectNode updated = existingNode.deepCopy();
                updated.put("count", count);
                entityService.updateItem("LanguageMentionAggregate", ENTITY_VERSION, technicalId, updated).get();
            } else {
                ObjectNode newAgg = objectMapper.createObjectNode();
                newAgg.put("language", lang);
                newAgg.put("timeSliceStart", day);
                newAgg.put("count", 1);
                entityService.addItem("LanguageMentionAggregate", ENTITY_VERSION, newAgg).get();
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