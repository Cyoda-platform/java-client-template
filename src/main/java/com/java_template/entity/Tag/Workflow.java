package com.java_template.entity.Tag;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import static com.java_template.common.config.Config.*;

@Component("Tag")
public class Workflow {

  private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

  public CompletableFuture<ObjectNode> isValidationFailed(ObjectNode entity) {
    boolean failed = !entity.hasNonNull("name") || entity.get("name").asText().trim().isEmpty();
    entity.put("validationFailed", failed);
    logger.info("Validation failed: {}", failed);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> isValidationSuccessful(ObjectNode entity) {
    boolean success = entity.hasNonNull("name") && !entity.get("name").asText().trim().isEmpty();
    entity.put("validationSuccessful", success);
    logger.info("Validation successful: {}", success);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> validateTagData(ObjectNode entity) {
    // Additional validation logic can be added here if needed
    logger.info("Validating tag data: {}", entity);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> handleValidationFailure(ObjectNode entity) {
    logger.error("Validation failed for tag data: {}", entity);
    entity.put("error", "Validation failed: 'name' is required and cannot be empty");
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> isStoreFailed(ObjectNode entity) {
    // Simulate store failure if a flag present or by default success
    boolean failed = entity.has("simulateStoreFailure") && entity.get("simulateStoreFailure").asBoolean();
    entity.put("storeFailed", failed);
    logger.info("Store failed: {}", failed);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> isStoreSuccessful(ObjectNode entity) {
    boolean success = !entity.has("storeFailed") || !entity.get("storeFailed").asBoolean();
    entity.put("storeSuccessful", success);
    logger.info("Store successful: {}", success);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> storeTagData(ObjectNode entity) {
    // Simulate storing tag data (e.g. setting stored flag)
    entity.put("stored", true);
    logger.info("Stored tag data: {}", entity);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> handleStoreFailure(ObjectNode entity) {
    logger.error("Store failed for tag data: {}", entity);
    entity.put("error", "Store operation failed");
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> isCacheFailed(ObjectNode entity) {
    // Simulate cache failure if a flag present or by default success
    boolean failed = entity.has("simulateCacheFailure") && entity.get("simulateCacheFailure").asBoolean();
    entity.put("cacheFailed", failed);
    logger.info("Cache failed: {}", failed);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> isCacheSuccessful(ObjectNode entity) {
    boolean success = !entity.has("cacheFailed") || !entity.get("cacheFailed").asBoolean();
    entity.put("cacheSuccessful", success);
    logger.info("Cache successful: {}", success);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> cacheTagData(ObjectNode entity) {
    // Simulate caching tag data (e.g. setting cached flag)
    entity.put("cached", true);
    logger.info("Cached tag data: {}", entity);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> handleCacheFailure(ObjectNode entity) {
    logger.error("Cache failed for tag data: {}", entity);
    entity.put("error", "Cache operation failed");
    return CompletableFuture.completedFuture(entity);
  }

}