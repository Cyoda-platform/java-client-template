package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
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
    static class SyncResponse {
        private String status;
        private String message;
        private int count;

        public SyncResponse(String status, String message, int count) {
            this.status = status;
            this.message = message;
            this.count = count;
        }
    }

    // Workflow function for pet entity - asynchronous processing before persistence
    private CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        logger.debug("Workflow processPet invoked with entity: {}", petEntity);

        try {
            // Normalize status field to lowercase if present and textual
            if (petEntity.has("status") && petEntity.get("status").isTextual()) {
                String status = petEntity.get("status").asText(Locale.ROOT).toLowerCase(Locale.ROOT);
                petEntity.put("status", status);
            }

            // Add a processing timestamp field
            petEntity.put("lastProcessedAt", System.currentTimeMillis());

            // Enrich category details if category present
            if (petEntity.has("category") && petEntity.get("category").isTextual()) {
                String categoryName = petEntity.get("category").asText();

                // Retrieve supplementary categoryDetails entity asynchronously
                return entityService.getItem("categoryDetails", ENTITY_VERSION, categoryName)
                    .thenCompose(categoryDetailsNode -> {
                        if (categoryDetailsNode != null && !categoryDetailsNode.isEmpty(null)) {
                            petEntity.set("categoryDetails", categoryDetailsNode);
                        } else {
                            petEntity.putObject("categoryDetails").put("info", "No details available");
                        }

                        // Fire-and-forget supplementary entity creation: petEvents log
                        ObjectNode petEvent = objectMapper.createObjectNode();
                        petEvent.put("eventType", "petProcessed");
                        petEvent.put("petName", petEntity.path("name").asText("unknown"));
                        petEvent.put("timestamp", System.currentTimeMillis());

                        // Add petEvents entity asynchronously, ignoring failures but logging them
                        return entityService.addItem("petEvents", ENTITY_VERSION, petEvent, Function.identity())
                            .handle((uuid, ex) -> {
                                if (ex != null) {
                                    logger.warn("Failed to add petEvent for pet {}: {}", petEntity.path("name").asText(), ex.toString());
                                } else {
                                    logger.debug("Added petEvent entity with id {}", uuid);
                                }
                                return petEntity;
                            });
                    });
            }

            // No enrichment needed, complete immediately
            return CompletableFuture.completedFuture(petEntity);

        } catch (Exception ex) {
            logger.error("Exception in processPet workflow", ex);
            // Return entity unchanged on error to avoid blocking persistence
            return CompletableFuture.completedFuture(petEntity);
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> syncPets(@RequestBody @Valid SyncRequest request) throws Exception {
        String sourceUrl = request.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            sourceUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available,pending,sold";
        }
        logger.info("Starting pet data sync from source: {}", sourceUrl);

        String rawJson = restTemplate.getForObject(sourceUrl, String.class);
        if (rawJson == null || rawJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch data from external source");
        }

        JsonNode rootNode = objectMapper.readTree(rawJson);
        if (!rootNode.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected JSON array from source");
        }

        List<ObjectNode> petsToAdd = new ArrayList<>();
        for (JsonNode petNode : rootNode) {
            ObjectNode petObjectNode = parsePetToObjectNode(petNode);
            petsToAdd.add(petObjectNode);
        }

        // Add pets with workflow function - entityService.addItems supports workflow function overload
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(ENTITY_NAME, ENTITY_VERSION, petsToAdd, this::processPet);
        List<UUID> createdIds = idsFuture.get();

        logger.info("Synchronized {} pets from external API", createdIds.size());
        return ResponseEntity.ok(new SyncResponse("success", "Pets data synchronized", createdIds.size()));
    }

    @PostMapping
    public ResponseEntity<ObjectNode> createPet(@RequestBody @Valid ObjectNode petEntity) throws ExecutionException, InterruptedException {
        // Add a new pet with workflow function applied before persistence
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petEntity, this::processPet);
        UUID technicalId = idFuture.get();

        petEntity.put("technicalId", technicalId.toString());
        logger.info("Created new pet with technicalId {}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(petEntity);
    }

    @PostMapping("/{petId}")
    public ResponseEntity<ObjectNode> updatePet(@PathVariable UUID petId, @RequestBody @Valid ObjectNode petEntityUpdate) throws Exception {
        // Verify pet existence before update
        CompletableFuture<ObjectNode> existingFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, petId);
        ObjectNode existingNode = existingFuture.get();
        if (existingNode == null || existingNode.isEmpty(null)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with ID " + petId);
        }

        petEntityUpdate.put("technicalId", petId.toString());

        // Update pet entity - no workflow function on update assumed (can be added similarly if needed)
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

    // Helper method to parse external JSON pet node into ObjectNode for persistence
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