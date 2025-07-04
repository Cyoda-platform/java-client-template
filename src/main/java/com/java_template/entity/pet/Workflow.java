package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("pet")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> normalizeStatus(ObjectNode petEntity) {
        logger.info("normalizeStatus called");
        JsonNode statusNode = petEntity.get("status");
        if (statusNode != null && statusNode.isTextual()) {
            petEntity.put("status", statusNode.asText().toLowerCase());
        }
        return CompletableFuture.completedFuture(petEntity);
    }

    public CompletableFuture<ObjectNode> addLastModifiedTimestamp(ObjectNode petEntity) {
        logger.info("addLastModifiedTimestamp called");
        petEntity.put("lastModified", Instant.now().toString());
        return CompletableFuture.completedFuture(petEntity);
    }

    public boolean isStatusPresent(ObjectNode petEntity) {
        JsonNode statusNode = petEntity.get("status");
        boolean present = statusNode != null && statusNode.isTextual();
        logger.info("isStatusPresent: {}", present);
        return present;
    }

    public boolean alwaysTrue(ObjectNode petEntity) {
        logger.info("alwaysTrue called: returning true");
        return true;
    }
}