package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component
public class PetWorkflow {

    private final RestTemplate restTemplate = new RestTemplate();

    // Use post construct if initialization needed
    @PostConstruct
    public void init() {
        // Initialization logic if needed
    }

    public CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Set default status if missing or blank
                if (!entity.hasNonNull("status") || entity.get("status").asText().isEmpty()) {
                    entity.put("status", "available");
                }

                String petName = entity.hasNonNull("name") ? entity.get("name").asText() : null;

                if (petName != null && !petName.isEmpty()) {
                    try {
                        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
                        JsonNode petsArray = restTemplate.getForObject(url, JsonNode.class);
                        if (petsArray != null && petsArray.isArray()) {
                            for (JsonNode petNode : petsArray) {
                                if (petName.equalsIgnoreCase(petNode.path("name").asText(""))) {
                                    String description = petNode.path("description").asText(null);
                                    if (description != null && !description.isEmpty()) {
                                        entity.put("description", description);
                                    }

                                    JsonNode category = petNode.path("category");
                                    if (category.has("name") && !category.get("name").asText().isEmpty()) {
                                        entity.put("type", category.get("name").asText());
                                    }

                                    if (petNode.hasNonNull("age") && petNode.get("age").isInt()) {
                                        entity.put("age", petNode.get("age").asInt());
                                    } else if (!entity.hasNonNull("age")) {
                                        entity.put("age", new Random().nextInt(10) + 1);
                                    }

                                    if (!entity.hasNonNull("friendly")) {
                                        entity.put("friendly", new Random().nextBoolean());
                                    }
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to enrich pet entity from external API in workflow", e);
                    }
                } else {
                    if (!entity.hasNonNull("age")) {
                        entity.put("age", new Random().nextInt(10) + 1);
                    }
                    if (!entity.hasNonNull("friendly")) {
                        entity.put("friendly", new Random().nextBoolean());
                    }
                }

                // TODO: Add supplementary entity logic here if needed, without modifying current entity directly

            } catch (Exception ex) {
                logger.error("Error in processPet workflow function", ex);
            }
            return entity;
        });
    }
}