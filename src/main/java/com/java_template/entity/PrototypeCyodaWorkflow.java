Certainly! To follow the best practice and required architecture, we should move all asynchronous logic related to entity enrichment, validation, or side-effects into the workflow function `process{entity_name}`.

---

### Key points for refactor:

- The `processPet` workflow function will receive the entity as an `ObjectNode` (generic JSON tree).
- We can modify this `ObjectNode` directly inside `processPet`.
- We can perform additional async calls or enrich the entity inside this function (e.g., fetch related data, add supplementary entities of different models).
- We **cannot** call add/update/delete on the same entity model inside `processPet` to avoid infinite recursion.
- The controller becomes very thin, mostly just forwarding the entity to `entityService.addItem` with the workflow function.
- Any async operations (like fetching from external APIs, adding supplementary entities) should be moved into `processPet`.

---

### What async logic do we have or can be moved?

- Currently your controller sync method fetches external data and adds many entities — this can remain a controller-level sync or be redesigned with workflow on those entities.
- The `createPet` endpoint: all logic around enriching/modifying pet entity should be moved to `processPet`.
- Potentially, if `processPet` needs to call other entityService methods (e.g., add supplementary entities), it can do so as long as those are different entity models.

---

### Updated code with workflow function using `ObjectNode`

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-pets")
public class CyodaEntityControllerPrototype {

    private static final String ENTITY_NAME = "pet";

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @Data
    static class SyncRequest {
        @Size(max = 200)
        private String sourceUrl;
    }

    @Data
    @lombok.AllArgsConstructor
    static class SyncResponse {
        private String status;
        private String message;
        private int count;
    }

    /**
     * Workflow function to process a Pet entity before persistence.
     * This function receives the entity as ObjectNode and can modify it directly.
     * You can perform async calls and add supplementary entities of other models here,
     * but you cannot add/update/delete the same entity model ('pet') here.
     */
    private CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        logger.debug("Processing pet entity in workflow function: {}", petEntity);

        // Example of modifying entity properties - normalize status to lowercase
        if (petEntity.has("status") && petEntity.get("status").isTextual()) {
            String status = petEntity.get("status").asText().toLowerCase(Locale.ROOT);
            petEntity.put("status", status);
        }

        // Example: add a timestamp field - lastProcessedAt
        petEntity.put("lastProcessedAt", System.currentTimeMillis());

        // Example: enrich category with additional info by fetching from some other entity or external API
        // This is async, so we can return a CompletableFuture

        // Let's say we want to get supplementary "categoryDetails" entity related to pet category

        if (petEntity.has("category") && petEntity.get("category").isTextual()) {
            String categoryName = petEntity.get("category").asText();

            // Simulate fetching supplementary entity 'categoryDetails' by categoryName:
            // Assuming entityModel = "categoryDetails", version = ENTITY_VERSION, key = categoryName

            return entityService.getItem("categoryDetails", ENTITY_VERSION, categoryName)
                .thenCompose(categoryDetailsNode -> {
                    if (categoryDetailsNode != null && !categoryDetailsNode.isEmpty(null)) {
                        petEntity.set("categoryDetails", categoryDetailsNode);
                    } else {
                        // Optionally add fallback or empty object
                        petEntity.putObject("categoryDetails").put("info", "No details available");
                    }

                    // Example of adding supplementary entity of different model (fire and forget)
                    // e.g., log pet creation event as "petEvents"
                    ObjectNode petEvent = objectMapper.createObjectNode();
                    petEvent.put("eventType", "petProcessed");
                    petEvent.put("petName", petEntity.path("name").asText("unknown"));
                    petEvent.put("timestamp", System.currentTimeMillis());

                    // Add supplementary entity of different model (allowed)
                    return entityService.addItem("petEvents", ENTITY_VERSION, petEvent, Function.identity())
                        .handle((uuid, ex) -> {
                            if (ex != null) {
                                logger.warn("Failed to add petEvent for pet {}: {}", petEntity.path("name").asText(), ex.toString());
                            } else {
                                logger.debug("Added petEvent entity with id {}", uuid);
                            }
                            // Return modified petEntity regardless of event add success
                            return petEntity;
                        });
                });
        }

        // If no category or no async call needed, return completed future immediately
        return CompletableFuture.completedFuture(petEntity);
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> syncPets(@RequestBody @Valid SyncRequest request) throws Exception {
        String sourceUrl = request.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            sourceUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available,pending,sold";
        }
        logger.info("Starting pet data sync from source: {}", sourceUrl);

        String rawJson = restTemplate.getForObject(sourceUrl, String.class);
        JsonNode rootNode = objectMapper.readTree(rawJson);
        if (!rootNode.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected JSON array from source");
        }

        List<ObjectNode> petsToAdd = new ArrayList<>();
        for (JsonNode petNode : rootNode) {
            ObjectNode petObjectNode = parsePetToObjectNode(petNode);
            petsToAdd.add(petObjectNode);
        }

        // Add pets with workflow function
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(ENTITY_NAME, ENTITY_VERSION, petsToAdd, this::processPet);
        List<UUID> createdIds = idsFuture.get();

        logger.info("Synchronized {} pets from external API", createdIds.size());
        return ResponseEntity.ok(new SyncResponse("success", "Pets data synchronized", createdIds.size()));
    }

    @PostMapping
    public ResponseEntity<ObjectNode> createPet(@RequestBody @Valid ObjectNode petEntity) throws ExecutionException, InterruptedException {
        // Directly call entityService.addItem with workflow function processPet
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petEntity, this::processPet);
        UUID technicalId = idFuture.get();

        petEntity.put("technicalId", technicalId.toString());
        logger.info("Created new pet with technicalId {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(petEntity);
    }

    @PostMapping("/{petId}")
    public ResponseEntity<ObjectNode> updatePet(@PathVariable UUID petId, @RequestBody @Valid ObjectNode petEntityUpdate) throws Exception {
        // Verify existence
        CompletableFuture<ObjectNode> existingFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, petId);
        ObjectNode existingNode = existingFuture.get();
        if (existingNode == null || existingNode.isEmpty(null)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with ID " + petId);
        }

        petEntityUpdate.put("technicalId", petId.toString());

        // UpdateItem - no workflow function for update assumed, keep it simple
        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, petId, petEntityUpdate);
        UUID updatedId = updatedIdFuture.get();
        petEntityUpdate.put("technicalId", updatedId.toString());

        logger.info("Updated pet with technicalId {}", updatedId);
        return ResponseEntity.ok(petEntityUpdate);
    }

    @GetMapping("/{petId}")
    public ResponseEntity<ObjectNode> getPetById(@PathVariable UUID petId) throws Exception {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, petId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty(null)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with ID " + petId);
        }
        return ResponseEntity.ok(node);
    }

    @GetMapping
    public ResponseEntity<ArrayNode> searchPets(
            @RequestParam(required = false) @Pattern(regexp = "available|pending|sold") String status,
            @RequestParam(required = false) @Size(max = 50) String category) throws Exception {

        if (status == null && category == null) {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
            ArrayNode arrayNode = itemsFuture.get();
            logger.info("Search pets without filters found {} results", arrayNode.size());
            return ResponseEntity.ok(arrayNode);
        } else {
            // Build condition JSON for filtering
            ObjectNode condition = objectMapper.createObjectNode();
            if (status != null) {
                condition.put("status", status);
            }
            if (category != null) {
                condition.put("category", category);
            }
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
            ArrayNode filteredArray = filteredItemsFuture.get();
            logger.info("Search pets with status='{}', category='{}' found {} results", status, category, filteredArray.size());
            return ResponseEntity.ok(filteredArray);
        }
    }

    /**
     * Parses external JSON pet node and maps it to ObjectNode suitable for persistence.
     * This is basic mapping without domain model conversion.
     */
    private ObjectNode parsePetToObjectNode(JsonNode petNode) {
        ObjectNode pet = objectMapper.createObjectNode();

        pet.put("name", petNode.path("name").asText(null));

        JsonNode categoryNode = petNode.path("category");
        if (!categoryNode.isMissingNode() && categoryNode.has("name")) {
            pet.put("category", categoryNode.get("name").asText(null));
        } else {
            pet.putNull("category");
        }

        pet.put("status", petNode.path("status").asText(null));

        // Convert tags array of objects with name field to array of strings
        ArrayNode tagsArray = objectMapper.createArrayNode();
        JsonNode tagsNode = petNode.path("tags");
        if (tagsNode.isArray()) {
            for (JsonNode tagNode : tagsNode) {
                if (tagNode.has("name")) {
                    tagsArray.add(tagNode.get("name").asText());
                }
            }
        }
        pet.set("tags", tagsArray);

        // photoUrls as array of strings
        ArrayNode photoUrlsArray = objectMapper.createArrayNode();
        JsonNode photosNode = petNode.path("photoUrls");
        if (photosNode.isArray()) {
            for (JsonNode photoNode : photosNode) {
                photoUrlsArray.add(photoNode.asText());
            }
        }
        pet.set("photoUrls", photoUrlsArray);

        return pet;
    }
}
```

---

### Summary of changes:
- Changed workflow function signature to `CompletableFuture<ObjectNode> processPet(ObjectNode petEntity)`.
- Moved **all** entity enrichment and async supplementary entity creation into `processPet`.
- `processPet` can fetch other entities (`categoryDetails`) and add supplementary entities (`petEvents`) asynchronously.
- Controller methods are now thin wrappers that:
  - Accept `ObjectNode` entity bodies,
  - Pass the entity and workflow function to `entityService.addItem`,
  - Return the persisted entity with `technicalId` attached.
- Removed domain POJO model from controller to work directly with `ObjectNode` to align with flexibility in workflow.
- Removed async logic from controller (no direct supplementary calls etc.).
- Search and get endpoints remain unchanged (they just fetch and return entities).
- Sync endpoint now calls `addItems` with workflow function to apply processing on each imported entity.

---

This approach centralizes all async enrichment, validation, and supplementary entity manipulations in the workflow function — exactly as required. Controllers remain clean, focused on HTTP concerns only.

If you want me to show similar workflow functions for update or delete, or further enrich the example, please ask!