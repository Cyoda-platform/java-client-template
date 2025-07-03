package com.java_template.entity.pet;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component("pet")
public class Workflow {

  private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
  private static final String EXTERNAL_PETSTORE_BASE = "https://petstore.swagger.io/v2/pet";
  private final RestTemplate restTemplate = new RestTemplate();

  public CompletableFuture<ObjectNode> isNewPet(ObjectNode entity) {
    UUID technicalId = null;
    if (entity.hasNonNull("technicalId")) {
      try {
        String idStr = entity.get("technicalId").asText();
        if (!idStr.isEmpty()) {
          technicalId = UUID.fromString(idStr);
        }
      } catch (IllegalArgumentException e) {
        logger.warn("Invalid technicalId format: {}", e.getMessage());
      }
    }
    boolean value = technicalId == null;
    entity.put("success", value);
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> callExternalAddPetApi(ObjectNode entity) {
    logger.info("Calling external addPet API asynchronously");
    CompletableFuture.runAsync(() -> {
      try {
        ObjectNode ps = prepareExternalPetPayload(entity);
        restTemplate.postForEntity(EXTERNAL_PETSTORE_BASE, ps.toString(), String.class);
        logger.info("External addPet API called asynchronously for pet name '{}'", ps.get("name").asText());
      } catch (Exception e) {
        logger.error("Failed to call external addPet API asynchronously", e);
      }
    });
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> callExternalUpdatePetApi(ObjectNode entity) {
    logger.info("Calling external updatePet API asynchronously");
    CompletableFuture.runAsync(() -> {
      try {
        ObjectNode ps = prepareExternalPetPayload(entity);
        restTemplate.put(EXTERNAL_PETSTORE_BASE, ps.toString());
        logger.info("External updatePet API called asynchronously for pet id '{}'", entity.get("technicalId").asText());
      } catch (Exception e) {
        logger.error("Failed to call external updatePet API asynchronously", e);
      }
    });
    return CompletableFuture.completedFuture(entity);
  }

  public CompletableFuture<ObjectNode> setDefaultStatusIfMissing(ObjectNode entity) {
    if (!entity.hasNonNull("status") || entity.get("status").asText().isEmpty()) {
      entity.put("status", "available");
      logger.info("Default status 'available' set for entity");
    }
    return CompletableFuture.completedFuture(entity);
  }

  private ObjectNode prepareExternalPetPayload(ObjectNode entity) {
    ObjectNode ps = entity.objectNode(); // Using entity's ObjectMapper internally if possible
    ps.put("id", 0);
    ObjectNode cat = ps.putObject("category");
    String petType = entity.hasNonNull("type") ? entity.get("type").asText() : "unknown";
    cat.put("id", 0);
    cat.put("name", petType);
    String petName = entity.hasNonNull("name") ? entity.get("name").asText() : "";
    ps.put("name", petName);
    ArrayNode photos = ps.putArray("photoUrls");
    if (entity.hasNonNull("photoUrls") && entity.get("photoUrls").isArray()) {
      entity.get("photoUrls").forEach(photos::add);
    }
    String status = entity.hasNonNull("status") ? entity.get("status").asText() : "available";
    ps.put("status", status);
    return ps;
  }
}