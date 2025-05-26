package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Workflow {

    private static final String NOTIFICATION_ENTITY = "Notification";
    private static final int ENTITY_VERSION = 1;

    // assume entityService and photoViewCounts are class members injected/set elsewhere
    private EntityService entityService;
    private java.util.concurrent.ConcurrentMap<String, Integer> photoViewCounts;

    public CompletableFuture<ObjectNode> processPhoto(ObjectNode photo) {
        // orchestrate workflow steps without business logic here
        return processUpdateTitle(photo)
                .thenCompose(this::processCreateNotification)
                .thenCompose(this::processInitViewCount);
    }

    private CompletableFuture<ObjectNode> processUpdateTitle(ObjectNode photo) {
        return CompletableFuture.supplyAsync(() -> {
            String title = photo.path("title").asText("");
            if (!title.endsWith(" [Processed]")) {
                photo.put("title", title + " [Processed]");
            }
            return photo;
        });
    }

    private CompletableFuture<ObjectNode> processCreateNotification(ObjectNode photo) {
        return CompletableFuture.supplyAsync(() -> {
            ObjectNode notification = JsonNodeFactory.instance.objectNode();
            notification.put("technicalId", UUID.randomUUID().toString());
            notification.put("message", "New cover photo added: " + photo.path("title").asText());
            notification.put("timestamp", Instant.now().toString());
            notification.put("read", false);
            entityService.addItem(NOTIFICATION_ENTITY, ENTITY_VERSION, notification, this::processNotification);
            return photo;
        });
    }

    private CompletableFuture<ObjectNode> processInitViewCount(ObjectNode photo) {
        return CompletableFuture.supplyAsync(() -> {
            if (photo.has("technicalId")) {
                photoViewCounts.putIfAbsent(photo.path("technicalId").asText(), 0);
            }
            return photo;
        });
    }

    private CompletableFuture<ObjectNode> processNotification(ObjectNode notification) {
        // placeholder for notification processing logic
        return CompletableFuture.completedFuture(notification);
    }
}