package com.java_template.entity.petfetchrequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.workflow.AbstractWorkflowHandler;
import com.java_template.common.workflow.WorkflowMethod;
import com.java_template.common.workflow.entity.PetFetchRequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Component("petfetchrequest")
public class Workflow extends AbstractWorkflowHandler<PetFetchRequestEntity> {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getEntityType() {
        return "petfetchrequest";
    }

    @Override
    public PetFetchRequestEntity createEntity(Object data) {
        if (data instanceof ObjectNode) {
            return new PetFetchRequestEntity((ObjectNode) data);
        }
        throw new IllegalArgumentException("Expected ObjectNode for PetFetchRequest entity creation");
    }

    @Override
    protected Class<PetFetchRequestEntity> getEntityClass() {
        return PetFetchRequestEntity.class;
    }

    // Workflow methods - automatically discovered via @WorkflowMethod annotation
    @WorkflowMethod(description = "No-operation method that returns entity unchanged")
    public CompletableFuture<PetFetchRequestEntity> noop(PetFetchRequestEntity entity) {
        return CompletableFuture.completedFuture(entity);
    }

    @WorkflowMethod(description = "Validates the fetch request and sets the valid flag")
    public CompletableFuture<PetFetchRequestEntity> isFetchRequestValid(PetFetchRequestEntity entity) {
        entity.validateRequest();
        return CompletableFuture.completedFuture(entity);
    }

    @WorkflowMethod(description = "Processes the pet fetch request by calling external API and storing results")
    public CompletableFuture<PetFetchRequestEntity> processPetFetchRequest(PetFetchRequestEntity entity) {
        String sourceUrl = entity.getSourceUrl();
        String status = entity.getStatus();

        if (!entity.isValid()) {
            logger.warn("Invalid fetchRequestEntity missing sourceUrl or status");
            return CompletableFuture.completedFuture(entity);
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
        }).thenApply(v -> entity);
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

    // Legacy methods for backward compatibility with existing registrar
    public CompletableFuture<ObjectNode> noop(ObjectNode entity) {
        PetFetchRequestEntity fetchEntity = new PetFetchRequestEntity(entity);
        return noop(fetchEntity).thenApply(PetFetchRequestEntity::toObjectNode);
    }

    public CompletableFuture<ObjectNode> isFetchRequestValid(ObjectNode fetchRequestEntity) {
        PetFetchRequestEntity entity = new PetFetchRequestEntity(fetchRequestEntity);
        return isFetchRequestValid(entity).thenApply(PetFetchRequestEntity::toObjectNode);
    }

    public CompletableFuture<ObjectNode> processPetFetchRequest(ObjectNode fetchRequestEntity) {
        PetFetchRequestEntity entity = new PetFetchRequestEntity(fetchRequestEntity);
        return processPetFetchRequest(entity).thenApply(PetFetchRequestEntity::toObjectNode);
    }
}