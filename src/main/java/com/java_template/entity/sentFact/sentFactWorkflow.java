package com.java_template.entity.sentFact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class sentFactWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(sentFactWorkflow.class);
    private final ObjectMapper objectMapper;

    public sentFactWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processSentFact(ObjectNode sentFactNode) {
        return processFetchFact(sentFactNode)
                .thenCompose(this::processSendEmails)
                .thenCompose(this::processLogSentFact);
    }

    private CompletableFuture<ObjectNode> processFetchFact(ObjectNode sentFactNode) {
        // Example placeholder: fact and sentCount are expected to be set already or fetched externally
        // Modify entity attributes if needed here
        return CompletableFuture.completedFuture(sentFactNode);
    }

    private CompletableFuture<ObjectNode> processSendEmails(ObjectNode sentFactNode) {
        String fact = sentFactNode.path("fact").asText(null);
        int sentCount = sentFactNode.path("sentCount").asInt(0);

        return CompletableFuture.runAsync(() -> {
            logger.info("Async sending fact to {} subscribers: {}", sentCount, fact);
            // TODO: Real email/send logic should be here
        }).thenApply(v -> sentFactNode);
    }

    private CompletableFuture<ObjectNode> processLogSentFact(ObjectNode sentFactNode) {
        // Example: add version info or timestamp to entity
        sentFactNode.put("entityVersion", ENTITY_VERSION);
        sentFactNode.put("lastProcessedAt", System.currentTimeMillis());
        return CompletableFuture.completedFuture(sentFactNode);
    }
}