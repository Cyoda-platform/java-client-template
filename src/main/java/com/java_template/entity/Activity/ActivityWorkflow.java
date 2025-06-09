package com.java_template.entity.Activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class ActivityWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(ActivityWorkflow.class);
    private final ObjectMapper objectMapper;

    // Main workflow orchestration method, no business logic here
    public CompletableFuture<ObjectNode> processActivity(ObjectNode entity) {
        return processAddTimestamp(entity)
                .thenCompose(this::processAddSupplementaryData);
    }

    // Adds processed timestamp to the entity
    private CompletableFuture<ObjectNode> processAddTimestamp(ObjectNode entity) {
        entity.put("processedTimestamp", System.currentTimeMillis());
        logger.info("Timestamp added to entity");
        return CompletableFuture.completedFuture(entity);
    }

    // Adds supplementary data asynchronously and updates the entity
    private CompletableFuture<ObjectNode> processAddSupplementaryData(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            JsonNode supplementaryData = fetchSupplementaryData();
            entity.set("supplementaryData", supplementaryData);
            logger.info("Supplementary data added: {}", supplementaryData);
            return entity;
        });
    }

    // Mock method to fetch supplementary data
    private JsonNode fetchSupplementaryData() {
        ObjectNode supplementaryData = objectMapper.createObjectNode();
        supplementaryData.put("exampleKey", "exampleValue");
        return supplementaryData;
    }
}