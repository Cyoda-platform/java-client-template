package com.java_template.entity.CommentIngestionTask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class CommentIngestionTaskWorkflow {

    private final ObjectMapper objectMapper;

    // TODO: Replace with actual config injection or service
    private final Map<String, List<String>> languageAliases = Map.of(
            "Go", List.of("go", "golang"),
            "Python", List.of("python"),
            "Java", List.of("java"),
            "Kotlin", List.of("kotlin"),
            "Rust", List.of("rust")
    );

    public CommentIngestionTaskWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processCommentIngestionTask(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate and initialize status
                if (!entity.hasNonNull("status")) {
                    entity.put("status", "initialized");
                }
                String taskId = entity.has("taskId") ? entity.get("taskId").asText() : "unknown";

                Instant start = processParseStartDate(entity);
                Instant end = processParseEndDate(entity);
                if (start == null || end == null) {
                    entity.put("status", "failed");
                    return entity;
                }

                logger.info("Starting ingestion workflow for taskId={}", taskId);

                entity.put("status", "fetching_ids");

                Integer maxItemId = processFetchMaxItemId();
                if (maxItemId == null) {
                    logger.error("Failed to fetch max item id for taskId={}", taskId);
                    entity.put("status", "failed");
                    return entity;
                }

                List<Integer> commentIds = processFetchCommentIdsInTimeWindow(maxItemId, start, end);

                entity.put("status", "fetching_comments");

                for (Integer commentId : commentIds) {
                    JsonNode commentJson = processFetchItemById(commentId);
                    if (commentJson == null) continue;
                    String text = commentJson.path("text").asText("");
                    Instant commentTime = processExtractCommentTime(commentJson);
                    if (commentTime == null) continue;
                    Set<String> detected = processDetectLanguages(text);
                    processUpdateAggregates(detected, commentTime);
                }

                entity.put("status", "completed");
                logger.info("Completed ingestion workflow for taskId={}", taskId);
            } catch (Exception e) {
                logger.error("Ingestion workflow failed: {}", e.getMessage(), e);
                entity.put("status", "failed");
            }
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> processParseStartDate(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (entity.hasNonNull("startDate")) {
                    Instant start = Instant.parse(entity.get("startDate").asText());
                    entity.put("startDateParsed", start.toString());
                    return entity;
                }
            } catch (Exception e) {
                logger.warn("Invalid startDate: {}", e.getMessage());
            }
            return entity;
        });
    }

    public CompletableFuture<ObjectNode> processParseEndDate(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (entity.hasNonNull("endDate")) {
                    Instant end = Instant.parse(entity.get("endDate").asText());
                    entity.put("endDateParsed", end.toString());
                    return entity;
                }
            } catch (Exception e) {
                logger.warn("Invalid endDate: {}", e.getMessage());
            }
            return entity;
        });
    }

    private Instant processParseStartDate(ObjectNode entity) {
        try {
            if (entity.hasNonNull("startDate")) {
                return Instant.parse(entity.get("startDate").asText());
            }
        } catch (Exception e) {
            logger.warn("Invalid startDate: {}", e.getMessage());
        }
        return null;
    }

    private Instant processParseEndDate(ObjectNode entity) {
        try {
            if (entity.hasNonNull("endDate")) {
                return Instant.parse(entity.get("endDate").asText());
            }
        } catch (Exception e) {
            logger.warn("Invalid endDate: {}", e.getMessage());
        }
        return null;
    }

    public Integer processFetchMaxItemId() {
        try {
            String url = "https://hacker-news.firebaseio.com/v0/maxitem.json";
            String resp = new java.net.http.HttpClient.Builder().build()
                    .send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString())
                    .body();
            if (resp == null || resp.isEmpty()) return null;
            return Integer.valueOf(resp.trim());
        } catch (Exception e) {
            logger.error("Failed to fetch maxitem: {}", e.getMessage());
            return null;
        }
    }

    public List<Integer> processFetchCommentIdsInTimeWindow(Integer maxItemId, Instant start, Instant end) {
        List<Integer> commentIds = new ArrayList<>();
        if (maxItemId == null) return commentIds;
        int limit = 5000;
        for (int id = maxItemId; id > maxItemId - limit && id > 0; id--) {
            JsonNode item = processFetchItemById(id);
            if (item == null) continue;
            if (!"comment".equalsIgnoreCase(item.path("type").asText(""))) continue;
            long timeSec = item.path("time").asLong(0);
            Instant commentTime = Instant.ofEpochSecond(timeSec);
            if (!commentTime.isBefore(start) && !commentTime.isAfter(end)) {
                commentIds.add(id);
            }
        }
        return commentIds;
    }

    public JsonNode processFetchItemById(int id) {
        try {
            String url = String.format("https://hacker-news.firebaseio.com/v0/item/%d.json", id);
            String resp = new java.net.http.HttpClient.Builder().build()
                    .send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString())
                    .body();
            if (resp == null || resp.isEmpty()) return null;
            return objectMapper.readTree(resp);
        } catch (Exception e) {
            logger.warn("Failed to fetch item {}: {}", id, e.getMessage());
            return null;
        }
    }

    private Instant processExtractCommentTime(JsonNode commentJson) {
        try {
            long timeSec = commentJson.path("time").asLong(0);
            if (timeSec == 0L) return null;
            return Instant.ofEpochSecond(timeSec);
        } catch (Exception e) {
            return null;
        }
    }

    public Set<String> processDetectLanguages(String text) {
        Set<String> detected = new HashSet<>();
        if (text == null || text.isEmpty()) return detected;
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

    public void processUpdateAggregates(Set<String> detectedLanguages, Instant commentTime) {
        if (detectedLanguages.isEmpty() || commentTime == null) return;
        String daySlice = commentTime.toString().substring(0, 10);
        // TODO: Implement aggregate storage, here just log for prototype
        detectedLanguages.forEach(lang -> logger.info("Increment aggregate count for {} at {}", lang, daySlice));
    }
}