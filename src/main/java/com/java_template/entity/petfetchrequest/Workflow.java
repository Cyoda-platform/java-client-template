package com.java_template.entity.petfetchrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Component("petfetchrequest")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();

    // noop action - does nothing, returns entity unchanged
    public CompletableFuture<ObjectNode> noop(ObjectNode entity) {
        return CompletableFuture.completedFuture(entity);
    }

    // condition function: returns true if fetchRequest has both sourceUrl and status non-null/non-empty
    public CompletableFuture<ObjectNode> isFetchRequestValid(ObjectNode fetchRequestEntity) {
        boolean valid = fetchRequestEntity.hasNonNull("sourceUrl") && !fetchRequestEntity.get("sourceUrl").asText().isEmpty()
                && fetchRequestEntity.hasNonNull("status") && !fetchRequestEntity.get("status").asText().isEmpty();
        fetchRequestEntity.put("valid", valid);
        return CompletableFuture.completedFuture(fetchRequestEntity);
    }

    // action function for fetching pets from external API, parsing and storing them asynchronously
    public CompletableFuture<ObjectNode> processPetFetchRequest(ObjectNode fetchRequestEntity) {
        String sourceUrl = fetchRequestEntity.path("sourceUrl").asText(null);
        String status = fetchRequestEntity.path("status").asText(null);

        if (sourceUrl == null || status == null) {
            logger.warn("Invalid fetchRequestEntity missing sourceUrl or status");
            return CompletableFuture.completedFuture(fetchRequestEntity);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI(sourceUrl + "?status=" + status);
                logger.info("[Workflow] Fetching pets from {}", uri);

                String raw = restTemplate.getForObject(uri, String.class);

                if (raw == null) {
                    logger.warn("[Workflow] No data fetched from source");
                    return;
                }

                JsonNode root = new ObjectMapper().readTree(raw);
                if (!root.isArray()) {
                    logger.warn("[Workflow] Expected JSON array for pets");
                    return;
                }

                List<ObjectNode> petsToAdd = new ArrayList<>();
                for (JsonNode node : root) {
                    ObjectNode petNode = petJsonNodeToObjectNode(node);
                    if (petNode != null) petsToAdd.add(petNode);
                }

                if (!petsToAdd.isEmpty()) {
                    // TODO: replace entityService.addItems with actual service call when available
                    // For prototype, just log count
                    logger.info("[Workflow] Fetched {} pets from external source", petsToAdd.size());
                }

            } catch (Exception e) {
                logger.error("[Workflow] Error fetching pets", e);
            }
        }).thenApply(v -> fetchRequestEntity);
    }

    // helper to convert JsonNode pet data to ObjectNode
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
                pet.putArray("tags").addAll(mapper.convertValue(petNode.get("tags").findValues("name"), mapper.getNodeFactory().arrayNode().getNodeType()));
            }
            // photoUrls as JSON array of strings
            if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
                pet.putArray("photoUrls").addAll(mapper.convertValue(petNode.get("photoUrls"), mapper.getNodeFactory().arrayNode().getNodeType()));
            }
            return pet;
        } catch (Exception e) {
            logger.error("Error converting pet JsonNode to ObjectNode", e);
            return null;
        }
    }
}