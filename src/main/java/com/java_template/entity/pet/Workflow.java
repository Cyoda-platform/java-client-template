package com.java_template.entity.pet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Component("pet")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final EntityService entityService;

    private final RestTemplate restTemplate = new RestTemplate();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    public CompletableFuture<ObjectNode> initializePet(ObjectNode pet) {
        logger.info("Initializing pet: name='{}', type='{}', status='{}'",
                pet.path("name").asText(""), pet.path("type").asText(""), pet.path("status").asText(""));
        if (!pet.hasNonNull("favorite")) {
            pet.put("favorite", false);
        }
        return CompletableFuture.completedFuture(pet);
    }

    public boolean hasPetType(ObjectNode pet) {
        String petType = pet.path("type").asText(null);
        boolean result = petType != null && !petType.isEmpty();
        logger.info("hasPetType check: type='{}' result={}", petType, result);
        return result;
    }

    public boolean hasNotPetType(ObjectNode pet) {
        boolean result = !hasPetType(pet);
        logger.info("hasNotPetType check result={}", result);
        return result;
    }

    public CompletableFuture<ObjectNode> enrichPetWithCategory(ObjectNode pet) {
        String petType = pet.path("type").asText(null);
        logger.info("Enriching pet with category for type='{}'", petType);
        if (petType == null) {
            return CompletableFuture.completedFuture(pet);
        }
        return entityService.getItemsByCondition("petCategory", ENTITY_VERSION,
                SearchConditionRequest.group("AND",
                        Condition.of("$.type", "EQUALS", petType)))
                .thenApply(categories -> {
                    if (categories != null && categories.size() > 0) {
                        JsonNode firstCategory = categories.get(0);
                        String categoryName = firstCategory.path("name").asText(null);
                        if (categoryName != null) {
                            pet.put("categoryName", categoryName);
                            logger.info("Set categoryName='{}' on pet", categoryName);
                        }
                    }
                    return pet;
                });
    }
}