package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private static final String ENTITY_NAME = "pet";
    private static final String ENTITY_VERSION = "1.0";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        logger.info("processPet workflow started for entity: {}", entity);

        if (entity.has("query") && entity.get("query").asBoolean(false)) {
            return processQuery(entity);
        }

        return processNormalPet(entity);
    }

    private CompletableFuture<ObjectNode> processQuery(ObjectNode entity) {
        String queryType = entity.hasNonNull("type") ? entity.get("type").asText() : null;
        String queryStatus = entity.hasNonNull("status") ? entity.get("status").asText() : null;
        String queryName = entity.hasNonNull("name") ? entity.get("name").asText() : null;

        logger.info("processPet detected query request: type={}, status={}, name={}", queryType, queryStatus, queryName);

        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=";
        if (queryStatus != null) {
            url += queryStatus;
        } else {
            url += "available,pending,sold";
        }

        // Fire async external API call and update entity directly
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Calling external Petstore API: {}", url);
                String responseJson = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
                JsonNode rootArray = objectMapper.readTree(responseJson);

                ArrayNode filteredPetsArray = objectMapper.createArrayNode();

                if (rootArray.isArray()) {
                    for (JsonNode petNode : rootArray) {
                        if (!(petNode instanceof ObjectNode)) continue;
                        ObjectNode petObj = (ObjectNode) petNode;

                        if (queryType != null) {
                            JsonNode categoryNode = petObj.path("category");
                            String petType = categoryNode.path("name").asText(null);
                            if (petType == null || !petType.equalsIgnoreCase(queryType)) {
                                continue;
                            }
                        }
                        if (queryName != null) {
                            String petName = petObj.path("name").asText("");
                            if (!petName.toLowerCase().contains(queryName.toLowerCase())) {
                                continue;
                            }
                        }
                        filteredPetsArray.add(petObj);
                    }
                } else {
                    logger.warn("Unexpected Petstore API response format");
                }

                // Add filtered pets as new entities (without recursion)
                for (JsonNode pet : filteredPetsArray) {
                    ObjectNode newPetEntity = objectMapper.createObjectNode();
                    newPetEntity.put("name", pet.path("name").asText(""));
                    newPetEntity.put("type", pet.path("category").path("name").asText(""));
                    newPetEntity.put("status", pet.path("status").asText(""));
                    ArrayNode photoUrls = objectMapper.createArrayNode();
                    if (pet.has("photoUrls") && pet.get("photoUrls").isArray()) {
                        pet.get("photoUrls").forEach(photoUrls::add);
                    }
                    newPetEntity.set("photoUrls", photoUrls);

                    // TODO: Add newPetEntity to storage or system in actual implementation
                    // Here just simulate by logging
                    logger.info("Simulated add of pet entity: {}", newPetEntity);
                }

                entity.put("processedAt", System.currentTimeMillis());
                entity.put("petsAddedCount", filteredPetsArray.size());
                entity.remove("query");

                return entity;

            } catch (Exception e) {
                logger.error("Exception in processPet workflow during query processing", e);
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<ObjectNode> processNormalPet(ObjectNode entity) {
        if (!entity.has("createdAt")) {
            entity.put("createdAt", System.currentTimeMillis());
        }
        return CompletableFuture.completedFuture(entity);
    }
}