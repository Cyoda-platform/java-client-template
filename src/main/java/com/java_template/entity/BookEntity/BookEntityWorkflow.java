package com.java_template.entity.BookEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class BookEntityWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(BookEntityWorkflow.class);

    private final ObjectMapper objectMapper;

    public BookEntityWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processBookEntity(ObjectNode entity) {
        if (entity == null) return CompletableFuture.completedFuture(null);

        return processTitle(entity)
                .thenCompose(updatedEntity -> processAudit(updatedEntity))
                .thenApply(updatedEntity -> updatedEntity);
    }

    private CompletableFuture<ObjectNode> processTitle(ObjectNode entity) {
        if (entity.hasNonNull("title")) {
            String title = entity.get("title").asText();
            if (!title.contains("[Processed]")) {
                entity.put("title", title + " [Processed]");
            }
        }
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processAudit(ObjectNode entity) {
        try {
            ObjectNode auditEntity = objectMapper.createObjectNode();
            String bookId = entity.path("bookId").asText(UUID.randomUUID().toString());
            auditEntity.put("bookId", bookId);
            auditEntity.put("processedAt", Instant.now().toString());
            auditEntity.put("processedBy", "processBookEntityWorkflow");
            auditEntity.put("originalTitle", entity.path("title").asText());
            // TODO fire-and-forget external addItem call - not allowed to call entityService.addItem on current entity
            // So this method only prepares audit entity, persistence should be handled outside
        } catch (Exception e) {
            logger.error("Failed to create audit entity in processAudit", e);
        }
        return CompletableFuture.completedFuture(entity);
    }
}