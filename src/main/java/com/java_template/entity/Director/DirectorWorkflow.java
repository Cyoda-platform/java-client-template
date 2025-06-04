package com.java_template.entity.Director;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class DirectorWorkflow {
    private static final Logger logger = LoggerFactory.getLogger(DirectorWorkflow.class);

    private final ObjectMapper objectMapper;

    public DirectorWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processDirector(ObjectNode entity) {
        logger.info("Starting workflow orchestration for Director: {}", entity.path("name").asText());

        return processSetProcessedFlag(entity)
                .thenCompose(this::processSetDefaultNationality);
    }

    private CompletableFuture<ObjectNode> processSetProcessedFlag(ObjectNode entity) {
        if (entity.has("processedByWorkflow") && entity.get("processedByWorkflow").asBoolean(false)) {
            logger.warn("Director entity already processed by workflow, skipping further processing");
            return CompletableFuture.completedFuture(entity);
        }
        entity.put("processedByWorkflow", true);
        return CompletableFuture.completedFuture(entity);
    }

    private CompletableFuture<ObjectNode> processSetDefaultNationality(ObjectNode entity) {
        if (!entity.hasNonNull("nationality") || entity.get("nationality").asText().trim().isEmpty()) {
            entity.put("nationality", "Unknown");
            logger.info("Set default nationality 'Unknown' for Director {}", entity.path("name").asText());
        }
        return CompletableFuture.completedFuture(entity);
    }
}