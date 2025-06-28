package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class PrototypeWorkflow {

    private final ConcurrentHashMap<String, ObjectNode> comments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ObjectNode> languageMentionsStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ObjectNode> aggregates = new ConcurrentHashMap<>();

    private final int batchSize = 50; // TODO: configurable
    private final Set<String> languageList = Set.of("Java", "Kotlin", "Python", "Go", "Rust"); // TODO: configurable

    private final int maxConcurrentTasks = 2; // TODO: configurable
    private int currentRunningTasks = 0;

    public CompletableFuture<ObjectNode> processPrototype(ObjectNode entity) {
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
                logStatus(entity, "Workflow: Starting ingestion processing for task " + entity.get("taskId").asText());
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
                logStatus(entity, "Workflow: Completed ingestion task " + entity.get("taskId").asText());
            } catch (Exception ex) {
                updateStatus(entity, "failed");
                entity.put("errorMessage", ex.getMessage());
                logStatus(entity, "Workflow: Error processing ingestion task " + entity.get("taskId").asText());
                logError(ex);
            } finally {
                synchronized (this) {
                    currentRunningTasks--;
                }
            }
            return entity;
        });
    }

    private void updateStatus(ObjectNode entity, String status) {
        entity.put("status", status);
    }

    private void logStatus(ObjectNode entity, String message) {
        log.info(message);
    }

    private void logError(Exception ex) {
        log.error("Exception in workflow", ex);
    }

    // Simulated fetching comment IDs from external Firebase API (mocked)
    private List<String> fetchCommentIds(String startTime, String endTime) {
        // TODO: Replace with real Firebase API call and filtering logic
        List<String> mockIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            mockIds.add("comment-" + (1000 + i));
        }
        return mockIds;
    }

    // Create comment entity with initial state
    private ObjectNode createCommentEntity(String commentId) {
        ObjectNode comment = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        comment.put("commentId", commentId);
        comment.put("text", "Sample comment text mentioning Java and Python.");
        comment.put("state", "fetched");
        return comment;
    }

    // Analyze batch of comments (mock AI)
    private void analyzeCommentsBatch(List<ObjectNode> batch) {
        // TODO: Replace with real OpenAI API call and parsing
        for (ObjectNode comment : batch) {
            comment.put("state", "analyzed");

            // Mock language mentions randomly
            Set<String> mentioned = Set.copyOf(languageList); // simplified: mention all for prototype
            languageMentionsStore.put(comment.get("commentId").asText(), createLanguageMentionsEntity(comment.get("commentId").asText(), mentioned));

            // Update aggregates (simplified)
            for (String lang : mentioned) {
                aggregates.merge(lang,
                        createLanguageMentionAggregate(lang, 1, "initial"),
                        (oldAgg, newAgg) -> {
                            int oldCount = oldAgg.get("count").asInt();
                            oldAgg.put("count", oldCount + 1);
                            oldAgg.put("state", "updated");
                            return oldAgg;
                        });
            }
        }
    }

    private ObjectNode createLanguageMentionsEntity(String commentId, Set<String> languagesMentioned) {
        ObjectNode lm = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        lm.put("commentId", commentId);
        var arr = lm.putArray("languagesMentioned");
        for (String lang : languagesMentioned) {
            arr.add(lang);
        }
        return lm;
    }

    private ObjectNode createLanguageMentionAggregate(String language, int count, String state) {
        ObjectNode agg = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        agg.put("language", language);
        agg.put("count", count);
        agg.put("state", state);
        return agg;
    }
}