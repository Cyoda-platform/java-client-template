package com.java_template.entity.gamescore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("gamescore")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public CompletableFuture<Object> processGameScore(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        if (objNode.has("status") && !objNode.get("status").isNull()) {
            String status = objNode.get("status").asText();
            objNode.put("status", status.toUpperCase());
            logger.info("Processed status field to uppercase: {}", status.toUpperCase());
        } else {
            logger.info("No status field to process");
        }
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> hasStatusField(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        boolean hasStatus = objNode.has("status") && !objNode.get("status").isNull() && !objNode.get("status").asText().isBlank();
        objNode.put("hasStatusField", hasStatus);
        logger.info("hasStatusField check: {}", hasStatus);
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> hasNotStatusField(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        boolean hasNotStatus = !(objNode.has("status") && !objNode.get("status").isNull() && !objNode.get("status").asText().isBlank());
        objNode.put("hasNotStatusField", hasNotStatus);
        logger.info("hasNotStatusField check: {}", hasNotStatus);
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> isStatusUppercase(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        boolean isUppercase = false;
        if (objNode.has("status") && !objNode.get("status").isNull()) {
            String status = objNode.get("status").asText();
            isUppercase = status.equals(status.toUpperCase());
        }
        objNode.put("isStatusUppercase", isUppercase);
        logger.info("isStatusUppercase check: {}", isUppercase);
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> isStatusNotUppercase(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        boolean isNotUppercase = false;
        if (objNode.has("status") && !objNode.get("status").isNull()) {
            String status = objNode.get("status").asText();
            isNotUppercase = !status.equals(status.toUpperCase());
        }
        objNode.put("isStatusNotUppercase", isNotUppercase);
        logger.info("isStatusNotUppercase check: {}", isNotUppercase);
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> storeEntity(Object entity) {
        // TODO: Implement actual persistence logic here
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        objNode.put("stored", true);
        logger.info("Mock storeEntity called, entity marked as stored");
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> isStored(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        boolean stored = objNode.has("stored") && objNode.get("stored").asBoolean();
        objNode.put("isStored", stored);
        logger.info("isStored check: {}", stored);
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> isNotStored(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        boolean notStored = !(objNode.has("stored") && objNode.get("stored").asBoolean());
        objNode.put("isNotStored", notStored);
        logger.info("isNotStored check: {}", notStored);
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> sendNotifications(Object entity) {
        // TODO: Implement actual notification sending here
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        objNode.put("notificationsSent", true);
        logger.info("Mock sendNotifications called, notificationsSent set to true");
        return CompletableFuture.completedFuture(objNode);
    }

    public CompletableFuture<Object> notificationsSent(Object entity) {
        ObjectNode objNode = (ObjectNode) objectMapper.valueToTree(entity);
        boolean sent = objNode.has("notificationsSent") && objNode.get("notificationsSent").asBoolean();
        objNode.put("notificationsSentCheck", sent);
        logger.info("notificationsSent check: {}", sent);
        return CompletableFuture.completedFuture(objNode);
    }
}