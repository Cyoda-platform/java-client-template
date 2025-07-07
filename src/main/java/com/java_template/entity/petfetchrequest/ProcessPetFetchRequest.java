package com.java_template.entity.petfetchrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.CyodaProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for fetching pets from external API and storing results.
 */
@Component("processpetfetchrequest")
public class ProcessPetFetchRequest implements CyodaProcessor<PetFetchRequest> {

    private static final Logger logger = LoggerFactory.getLogger(ProcessPetFetchRequest.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public ProcessPetFetchRequest(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<PetFetchRequest> process(PetFetchRequest entity) {
        logger.info("processPetFetchRequest processor called");

        if (!entity.isValid()) {
            logger.warn("Invalid fetchRequestEntity missing sourceUrl or status");
            return CompletableFuture.completedFuture(entity);
        }

        String sourceUrl = entity.getSourceUrl();
        String status = entity.getStatus();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = new URI(sourceUrl + "?status=" + status);
                logger.info("[Processor] Fetching pets from {}", uri);

                String raw = restTemplate.getForObject(uri, String.class);

                if (raw == null) {
                    logger.warn("[Processor] No data fetched from source");
                    return entity;
                }

                JsonNode root = new ObjectMapper().readTree(raw);
                if (!root.isArray()) {
                    logger.warn("[Processor] Expected JSON array for pets");
                    return entity;
                }

                List<ObjectNode> petsToAdd = new ArrayList<>();
                for (JsonNode node : root) {
                    ObjectNode petNode = petJsonNodeToObjectNode(node);
                    if (petNode != null) petsToAdd.add(petNode);
                }

                if (!petsToAdd.isEmpty()) {
                    // TODO: replace entityService.addItems with actual service call when available
                    // For prototype, just log count
                    logger.info("[Processor] Fetched {} pets from external source", petsToAdd.size());
                }

                return entity;

            } catch (Exception e) {
                logger.error("[Processor] Error fetching pets", e);
                return entity;
            }
        });
    }

    @Override
    public Class<PetFetchRequest> getEntityType() {
        return PetFetchRequest.class;
    }
    
    // Helper method to convert JsonNode pet data to ObjectNode
    private ObjectNode petJsonNodeToObjectNode(JsonNode petNode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode pet = mapper.createObjectNode();

            pet.put("id", petNode.path("id").asLong());
            pet.put("name", petNode.path("name").asText(""));
            if (petNode.has("status")) {
                pet.put("status", petNode.get("status").asText(""));
            }
            if (petNode.has("category") && petNode.get("category").has("name")) {
                pet.put("category", petNode.get("category").get("name").asText(""));
            }
            // tags as JSON array of strings
            if (petNode.has("tags") && petNode.get("tags").isArray()) {
                ArrayNode tagsArray = mapper.createArrayNode();
                for (JsonNode tag : petNode.get("tags")) {
                    if (tag.isObject()) {
                        String tagName = tag.path("name").asText(null);
                        if (tagName != null) tagsArray.add(tagName);
                    } else if (tag.isTextual()) {
                        tagsArray.add(tag.asText());
                    }
                }
                pet.set("tags", tagsArray);
            }
            // photoUrls as JSON array of strings
            if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
                ArrayNode photosArray = mapper.createArrayNode();
                for (JsonNode photoUrl : petNode.get("photoUrls")) {
                    if (photoUrl.isTextual()) {
                        photosArray.add(photoUrl.asText());
                    }
                }
                pet.set("photoUrls", photosArray);
            }
            return pet;
        } catch (Exception e) {
            logger.error("Error converting pet JsonNode to ObjectNode", e);
            return null;
        }
    }
}
