package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class PetWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(PetWorkflow.class);
    private final ObjectMapper objectMapper;

    public CompletableFuture<ObjectNode> processPet(@NotNull ObjectNode entity) {
        return processValidate(entity)
                .thenCompose(this::processEnrich)
                .thenCompose(this::processAsyncOps);
    }

    private CompletableFuture<ObjectNode> processValidate(ObjectNode entity) {
        if (!entity.has("category") ||
            !entity.get("category").has("name") ||
            entity.get("category").get("name").asText().trim().isEmpty()) {
            CompletableFuture<ObjectNode> failed = new CompletableFuture<>();
            failed.completeExceptionally(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name must be provided"));
            return failed;
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processEnrich(ObjectNode entity) {
        if (!entity.has("createdAt") || entity.get("createdAt").isNull()) {
            entity.put("createdAt", System.currentTimeMillis());
        }
        if (!entity.has("status") || entity.get("status").asText().trim().isEmpty()) {
            entity.put("status", "available");
        }
        entity.put("entityVersion", ENTITY_VERSION);
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processAsyncOps(ObjectNode entity) {
        // TODO: Add real async operations if needed e.g. external calls
        return CompletableFuture.completedFuture(entity);
    }
}