package com.java_template.entity.Book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class BookWorkflow {

    private final ObjectMapper objectMapper;

    public BookWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Orchestration method - no business logic here
    public CompletableFuture<ObjectNode> processBook(ObjectNode bookNode) {
        log.info("Starting workflow orchestration for book: {}", bookNode);
        return processFetchAdditionalInfo(bookNode)
                .thenCompose(this::processMarkProcessed)
                .thenApply(node -> {
                    log.info("Workflow orchestration complete for book: {}", node);
                    return node;
                });
    }

    // Fetch additional info asynchronously and update entity
    private CompletableFuture<ObjectNode> processFetchAdditionalInfo(ObjectNode bookNode) {
        return CompletableFuture.supplyAsync(() -> {
            String title = bookNode.path("title").asText();
            String additionalInfo = fetchAdditionalBookInfo(title);
            bookNode.put("additionalInfo", additionalInfo);
            return bookNode;
        });
    }

    // Mark book as processed
    private CompletableFuture<ObjectNode> processMarkProcessed(ObjectNode bookNode) {
        return CompletableFuture.supplyAsync(() -> {
            bookNode.put("processed", true);
            return bookNode;
        });
    }

    // Simulated external fetch - blocking call
    private String fetchAdditionalBookInfo(String title) {
        try {
            Thread.sleep(1000); // Simulate delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Additional info for " + title;
    }

}