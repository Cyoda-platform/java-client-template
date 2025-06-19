package com.java_template.entity.Activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class ActivityWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(ActivityWorkflow.class);

    private final ObjectMapper objectMapper;

    public CompletableFuture<ObjectNode> processActivity(ObjectNode entity) {
        return processAddProcessedTimestamp(entity)
                .thenCompose(this::processEnrichTitleLength)
                .thenCompose(this::processAddLogEntry)
                .thenCompose(this::processSendNotification);
    }

    private CompletableFuture<ObjectNode> processAddProcessedTimestamp(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            entity.put("processedTimestamp", Instant.now().toString());
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processEnrichTitleLength(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            String title = entity.path("title").asText("");
            if (!title.isEmpty()) {
                entity.put("titleLength", title.length());
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processAddLogEntry(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode logEntryNode = objectMapper.createObjectNode();
                String title = entity.path("title").asText("");
                logEntryNode.put("message", "Ingested activity with title: " + title);
                logEntryNode.put("ingestedAt", Instant.now().toString());

                // TODO: The entityService.addItem call is not allowed on current entity, 
                // so fire and forget elsewhere or adapt accordingly outside this workflow.
                // Here just simulate without persistence.
                CompletableFuture.runAsync(() -> {
                    // simulate async persistence of logEntryNode and processing
                    logger.info("Simulated adding LogEntry supplementary entity: {}", logEntryNode.toString());
                });
            } catch (Exception e) {
                logger.error("Exception while creating supplementary LogEntry entity", e);
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSendNotification(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String title = entity.path("title").asText("");
                logger.info("Sending notification for activity titled '{}'", title);
                // Simulate notification sending, fire and forget
            } catch (Exception e) {
                logger.error("Failed to send notification", e);
            }
            return entity;
        });
    }

}