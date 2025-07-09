package com.java_template.entity.AdoptionRequest;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component("AdoptionRequest")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    public CompletableFuture<ObjectNode> submitRequest(ObjectNode entity) {
        logger.info("Executing submitRequest action");
        if (!entity.has("status")) {
            entity.put("status", "pending");
        }
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> reviewRequest(ObjectNode entity) {
        logger.info("Executing reviewRequest action");
        // Additional logic can be added here if needed
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> approveRequest(ObjectNode entity) {
        logger.info("Executing approveRequest action");
        entity.put("status", "approved");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> rejectRequest(ObjectNode entity) {
        logger.info("Executing rejectRequest action");
        entity.put("status", "rejected");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> completeAdoption(ObjectNode entity) {
        logger.info("Executing completeAdoption action");
        entity.put("status", "completed");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> closeRequest(ObjectNode entity) {
        logger.info("Executing closeRequest action");
        entity.put("status", "closed");
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isApproved(ObjectNode entity) {
        logger.info("Evaluating isApproved condition");
        boolean approved = false;
        if (entity.has("approvalDecision")) {
            approved = "approve".equalsIgnoreCase(entity.get("approvalDecision").asText());
        }
        entity.put("success", approved);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> isRejected(ObjectNode entity) {
        logger.info("Evaluating isRejected condition");
        boolean rejected = false;
        if (entity.has("approvalDecision")) {
            rejected = "reject".equalsIgnoreCase(entity.get("approvalDecision").asText());
        }
        entity.put("success", rejected);
        return CompletableFuture.completedFuture(entity);
    }
}