package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "pet";
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function to process the pet entity asynchronously before persistence.
     * Handles adoption logic, sets adoption fields, and may create supplementary entities.
     * Must NOT call add/update/delete on the same entityModel to avoid recursion.
     */
    private CompletableFuture<JsonNode> processPet(JsonNode entity) {
        ObjectNode petNode = (ObjectNode) entity;

        // Handle adoption processing if "adopt" flag is present and true
        if (petNode.has("adopt") && petNode.get("adopt").asBoolean(false)) {
            logger.info("Processing adoption workflow inside processPet for pet id={}", petNode.has("id") ? petNode.get("id").asText() : "N/A");

            // Check if pet already adopted
            if (petNode.has("adopted") && petNode.get("adopted").asBoolean(false)) {
                // Mark error field for adoption conflict
                petNode.put("error", "Pet already adopted");
            } else {
                // Mark as adopted
                petNode.put("adopted", true);

                // Ensure adopterName exists and is non-empty, fallback to "Unknown"
                if (petNode.hasNonNull("adopterName") && !petNode.get("adopterName").asText().isBlank()) {
                    // use provided adopterName
                } else {
                    petNode.put("adopterName", "Unknown");
                }

                petNode.put("adoptedAt", Instant.now().toString());

                // Remove the 'adopt' flag so it won't persist unnecessarily
                petNode.remove("adopt");

                // Example of adding supplementary entity asynchronously (fire and forget)
                // Create adoption record entity of a different model
                try {
                    ObjectNode adoptionRecord = objectMapper.createObjectNode();
                    adoptionRecord.put("petId", petNode.has("id") ? petNode.get("id").asLong() : -1L);
                    adoptionRecord.put("adopterName", petNode.get("adopterName").asText());
                    adoptionRecord.put("adoptedAt", petNode.get("adoptedAt").asText());
                    // Adding supplementary entity, no recursion risk
                    entityService.addItem("adoptionRecord", ENTITY_VERSION, adoptionRecord, adoptionRecordEntity -> CompletableFuture.completedFuture(adoptionRecordEntity));
                } catch (Exception e) {
                    logger.error("Failed to add supplementary adoption record entity", e);
                    // Do not fail main workflow for supplementary entity failure
                }
            }
        }

        // Here you can add other entity processing logic before persistence if needed

        return CompletableFuture.completedFuture(petNode);
    }

    @PostMapping("/search")
    public List<JsonNode> searchPets(@Valid @RequestBody SearchRequest request) {
        logger.info("Received search request: type='{}', status='{}'", request.getType(), request.getStatus());
        String statusParam = (request.getStatus() != null && !request.getStatus().isBlank()) ? request.getStatus() : "available";

        List<Condition> conditionsList = new ArrayList<>();
        if (request.getType() != null && !request.getType().isBlank()) {
            conditionsList.add(Condition.of("$.category.name", "IEQUALS", request.getType()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            conditionsList.add(Condition.of("$.status", "EQUALS", statusParam));
        } else {
            conditionsList.add(Condition.of("$.status", "EQUALS", "available"));
        }

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND",
                conditionsList.toArray(new Condition[0]));

        ArrayNode petsNode = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                conditionRequest
        ).join();

        if (petsNode == null || !petsNode.isArray()) {
            throw new ResponseStatusException(502, "Invalid response from EntityService");
        }

        List<JsonNode> results = new ArrayList<>();
        petsNode.forEach(results::add);
        return results;
    }

    @GetMapping
    public List<JsonNode> getCachedPets() {
        logger.info("Returning all pets from EntityService");
        ArrayNode petsNode = entityService.getItems(ENTITY_NAME, ENTITY_VERSION).join();
        if (petsNode == null || !petsNode.isArray()) {
            throw new ResponseStatusException(502, "Invalid response from EntityService");
        }
        return List.copyOf(petsNode);
    }

    /**
     * Adopt a pet by updating the entity with adoption info.
     * Adoption logic moved into processPet workflow function.
     */
    @PostMapping("/adopt")
    public AdoptionResponse adoptPet(@Valid @RequestBody AdoptionRequest request) {
        logger.info("Adopt pet request: petId={}, adopterName={}", request.getPetId(), request.getAdopterName());

        // Fetch existing pet entity
        ObjectNode petNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, request.getPetId()).join();
        if (petNode == null) {
            throw new ResponseStatusException(404, "Pet not found");
        }

        // Check if pet already adopted early to avoid unnecessary processing
        if (petNode.has("adopted") && petNode.get("adopted").asBoolean(false)) {
            throw new ResponseStatusException(409, "Pet already adopted");
        }

        // Add adoption flag and adopterName to trigger workflow logic
        petNode.put("adopt", true);
        petNode.put("adopterName", request.getAdopterName());

        // Process entity with workflow function before persistence
        JsonNode processedEntity = processPet(petNode).join();

        // Check for workflow error marker
        if (processedEntity.has("error")) {
            throw new ResponseStatusException(409, processedEntity.get("error").asText());
        }

        // Persist updated entity
        entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, request.getPetId(), processedEntity).join();

        return new AdoptionResponse(true, "Pet adopted successfully", request.getPetId());
    }

    /**
     * Create a new pet entity.
     * Applies workflow processPet asynchronously before persistence.
     */
    @PostMapping
    public CompletableFuture<java.util.UUID> createPet(@Valid @RequestBody JsonNode petData) {
        logger.info("Creating new pet with workflow processing");
        return entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                petData,
                this::processPet
        );
    }

    @GetMapping("/{id}")
    public JsonNode getPetById(@PathVariable("id") Long petId) {
        logger.info("Fetch details for id={}", petId);
        ObjectNode petNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, petId).join();
        if (petNode == null) {
            throw new ResponseStatusException(404, "Pet not found");
        }
        return petNode;
    }

    @Data
    public static class SearchRequest {
        @Size(max = 20)
        private String type;
        @Size(max = 20)
        private String status;
    }

    @Data
    public static class AdoptionRequest {
        @NotNull
        private Long petId;
        @NotBlank
        private String adopterName;
    }

    @Data
    public static class AdoptionResponse {
        private final boolean success;
        private final String message;
        private final Long petId;
    }
}