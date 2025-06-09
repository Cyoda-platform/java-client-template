package com.java_template.entity.Photo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class PhotoWorkflow {

    private final ObjectMapper objectMapper;

    public PhotoWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Main workflow orchestration method - no business logic here
    public CompletableFuture<ObjectNode> processPhoto(ObjectNode photo) {
        return processEnsureTitle(photo)
                .thenCompose(this::processIncrementViews)
                .thenCompose(this::processAdditionalTasks);
    }

    // Ensure photo has a title, add default if missing
    public CompletableFuture<ObjectNode> processEnsureTitle(ObjectNode photo) {
        if (!photo.has("title") || photo.get("title").asText().isEmpty()) {
            photo.put("title", "Untitled");
        }
        return CompletableFuture.completedFuture(photo);
    }

    // Increment views count
    public CompletableFuture<ObjectNode> processIncrementViews(ObjectNode photo) {
        int views = photo.has("views") ? photo.get("views").asInt() : 0;
        photo.put("views", views + 1);
        return CompletableFuture.completedFuture(photo);
    }

    // Placeholder for any additional asynchronous tasks
    public CompletableFuture<ObjectNode> processAdditionalTasks(ObjectNode photo) {
        // TODO: Add any async tasks here, e.g. notification triggers, report updates
        return CompletableFuture.completedFuture(photo);
    }
}