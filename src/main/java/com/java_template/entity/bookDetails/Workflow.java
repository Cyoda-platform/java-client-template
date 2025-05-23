package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

public class Workflow {

    private final ObjectMapper objectMapper;
    private final EntityService entityService;

    public Workflow(ObjectMapper objectMapper, EntityService entityService) {
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> processBookDetails(ObjectNode entity) {
        return processEnsureGenre(entity)
                .thenCompose(this::processAddBookMetadata)
                .thenCompose(this::processAddUserSearchHistory);
    }

    private CompletableFuture<ObjectNode> processEnsureGenre(ObjectNode entity) {
        if (!entity.hasNonNull("genre") || entity.get("genre").asText().isEmpty()) {
            entity.put("genre", "Unknown");
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processAddBookMetadata(ObjectNode entity) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("bookId", entity.get("bookId").asText());
        metadata.put("title", entity.get("title").asText());
        metadata.put("timestamp", Instant.now().toString());
        entityService.addItem("bookMetadata", ENTITY_VERSION, metadata, e -> CompletableFuture.completedFuture(e));
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processAddUserSearchHistory(ObjectNode entity) {
        if (entity.hasNonNull("userId")) {
            ObjectNode userSearch = objectMapper.createObjectNode();
            userSearch.put("userId", entity.get("userId").asText());
            userSearch.put("bookId", entity.get("bookId").asText());
            userSearch.put("searchedAt", Instant.now().toString());
            entityService.addItem("userSearchHistory", ENTITY_VERSION, userSearch, e -> CompletableFuture.completedFuture(e));
        }
        return CompletableFuture.completedFuture(entity);
    }
}