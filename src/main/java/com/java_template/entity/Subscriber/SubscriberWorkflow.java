package com.java_template.entity.Subscriber;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class SubscriberWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(SubscriberWorkflow.class);

    private final ObjectMapper objectMapper;

    public SubscriberWorkflow(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<ObjectNode> processSubscriber(ObjectNode entity) {
        // workflow orchestration only
        return processSetSubscribedAt(entity)
                .thenCompose(this::processAdditionalEnrichment);
    }

    public CompletableFuture<ObjectNode> processSetSubscribedAt(ObjectNode entity) {
        try {
            if (!entity.hasNonNull("subscribedAt")) {
                entity.put("subscribedAt", Instant.now().toString());
            }
            return CompletableFuture.completedFuture(entity);
        } catch (Exception e) {
            logger.error("Error in processSetSubscribedAt", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<ObjectNode> processAdditionalEnrichment(ObjectNode entity) {
        try {
            // TODO: Add any additional async enrichment logic here if needed
            return CompletableFuture.completedFuture(entity);
        } catch (Exception e) {
            logger.error("Error in processAdditionalEnrichment", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}