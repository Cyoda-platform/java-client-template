package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component("pet")
public class Workflow {

  public CompletableFuture<ObjectNode> isNotAlreadyProcessed(ObjectNode entity) {
    boolean value = !entity.has("processed") || !entity.get("processed").asBoolean();
    entity.put("success", value);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> isAlreadyProcessed(ObjectNode entity) {
    boolean value = entity.has("processed") && entity.get("processed").asBoolean();
    entity.put("success", value);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> prefixProcessedName(ObjectNode entity) {
    if (entity.has("name")) {
      String name = entity.get("name").asText();
      entity.put("name", "Processed_" + name);
    }
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> updateFunFact(ObjectNode entity) {
    entity.put("funFact", "Cats sleep 70% of their lives.");
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> enforceDefaultStatus(ObjectNode entity) {
    if (!entity.has("status")) {
      entity.put("status", "pending");
    }
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> asyncNotifyPetProcessed(ObjectNode entity) {
    entity.put("notificationSent", true);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> addPetMetadata(ObjectNode entity) {
    entity.put("processed", true);
    entity.put("version", ENTITY_VERSION);
    return CompletableFuture.completedFuture(entity);
  }

}