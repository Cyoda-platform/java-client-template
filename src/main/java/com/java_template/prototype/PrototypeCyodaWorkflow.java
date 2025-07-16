package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/entity/pets")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME = "pet";

    // Request DTOs
    public static class PetRequest {
        @NotBlank
        @Pattern(regexp = "fetch|add|update", flags = Pattern.Flag.CASE_INSENSITIVE)
        public String action;
        @Min(1)
        public Long id; // required for update and optionally for fetch
        @Size(min = 1, max = 100)
        public String name; // required for add/update
        @Size(min = 1, max = 50)
        public String category; // required for add/update
        @Pattern(regexp = "available|pending|sold", flags = Pattern.Flag.CASE_INSENSITIVE)
        public String status; // required for add/update
    }

    public static class SearchRequest {
        @Pattern(regexp = "available|pending|sold", flags = Pattern.Flag.CASE_INSENSITIVE)
        public String status;
        @Size(min = 1, max = 50)
        public String category;
        @Size(min = 1, max = 50)
        public String nameContains;
    }

    // Pet model for serialization/deserialization convenience
    public static class Pet {
        public UUID technicalId;
        public Long id;
        public String name;
        public String category;
        public String status;
    }

    /**
     * Workflow function applied asynchronously before persistence.
     * Receives entity as ObjectNode, can modify it directly.
     * Can get/add other entities of different entityModels asynchronously.
     * Cannot add/update/delete entities of the same model 'pet'.
     */
    private final Function<Object, CompletableFuture<Object>> processPet = (entity) -> {
        ObjectNode petNode = (ObjectNode) entity;

        // Defensive: if entity is null or not ObjectNode, fail fast
        if (petNode == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Entity object is null"));
        }

        // Enrich pet entity if missing core fields name or category by external API call
        if (!petNode.hasNonNull("name") || !petNode.hasNonNull("category")) {
            try {
                Long id = (petNode.hasNonNull("id") && petNode.get("id").canConvertToLong())
                        ? petNode.get("id").asLong()
                        : 1L;
                String externalUrl = "https://petstore.swagger.io/v2/pet/" + id;
                String resp = restTemplate.getForObject(externalUrl, String.class);
                if (resp != null) {
                    JsonNode root = objectMapper.readTree(resp);
                    if (root.hasNonNull("name")) petNode.put("name", root.get("name").asText());
                    if (root.has("category") && root.get("category").hasNonNull("name")) {
                        petNode.put("category", root.get("category").get("name").asText());
                    }
                    if (root.hasNonNull("status")) petNode.put("status", root.get("status").asText());
                    if (root.hasNonNull("id") && !petNode.hasNonNull("id")) {
                        petNode.put("id", root.get("id").asLong());
                    }
                }
            } catch (Exception ex) {
                logger.warn("Failed to enrich pet entity from external source: {}", ex.getMessage());
                // Swallow exception to allow persistence with partial data
            }
        }

        // Normalize status to lowercase if present and valid
        if (petNode.hasNonNull("status")) {
            String status = petNode.get("status").asText().toLowerCase(Locale.ROOT);
            if (status.equals("available") || status.equals("pending") || status.equals("sold")) {
                petNode.put("status", status);
            } else {
                // Invalid status value, fix or remove
                petNode.put("status", "available"); // default safe value
            }
        } else {
            petNode.put("status", "available"); // default if missing
        }

        // Set default category if missing or empty
        if (!petNode.hasNonNull("category") || petNode.get("category").asText().trim().isEmpty()) {
            petNode.put("category", "unknown");
        }

        // Ensure name is non-null and trimmed, else assign "Unnamed"
        if (!petNode.hasNonNull("name") || petNode.get("name").asText().trim().isEmpty()) {
            petNode.put("name", "Unnamed");
        } else {
            petNode.put("name", petNode.get("name").asText().trim());
        }

        // Example: async fire-and-forget task: log external system or metrics
        CompletableFuture.runAsync(() -> {
            logger.info("Async side-effect: Pet processed before persistence: id={}, name={}",
                    petNode.path("id").asText("N/A"), petNode.path("name").asText("N/A"));
            // Insert any additional async logic here, e.g. metrics, notifications, etc.
        });

        return CompletableFuture.completedFuture(petNode);
    };

    /**
     * Workflow function for update operation.
     * Updates can also benefit from normalization and async side-effects.
     * This can be added if entityService.updateItem supports workflow.
     * For now, updateItem does not support workflow, so logic remains in controller.
     */
    // private final Function<Object, CompletableFuture<Object>> processPetUpdate = (entity) -> {
    //     // Similar normalization as processPet can be done here if needed
    //     return CompletableFuture.completedFuture(entity);
    // };

    @PostMapping
    public ResponseEntity<?> postPets(@Valid @RequestBody PetRequest request) throws ExecutionException, InterruptedException {
        String action = request.action.toLowerCase(Locale.ROOT).trim();
        logger.info("POST /entity/pets action={}", action);
        switch (action) {
            case "fetch": {
                // Defensive: allow fetch with id or default id=1
                ObjectNode petNode = objectMapper.createObjectNode();
                if (request.id != null && request.id > 0) {
                    petNode.put("id", request.id);
                } else {
                    petNode.put("id", 1L);
                }

                // Add item with workflow that enriches entity before persistence
                CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode, this::processPet);
                UUID technicalId = idFuture.get();
                petNode.put("technicalId", technicalId.toString());

                Pet pet = objectMapper.convertValue(petNode, Pet.class);

                logger.info("Fetched and persisted pet {}", pet);
                return ResponseEntity.ok(Map.of("success", true, "pet", pet, "message", "Fetched pet"));
            }
            case "add": {
                if (request.name == null || request.category == null || request.status == null) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Missing fields for add");
                }
                ObjectNode petNode = objectMapper.createObjectNode();
                petNode.put("name", request.name);
                petNode.put("category", request.category);
                petNode.put("status", request.status);

                CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode, this::processPet);
                UUID technicalId = idFuture.get();
                petNode.put("technicalId", technicalId.toString());

                Pet pet = objectMapper.convertValue(petNode, Pet.class);

                logger.info("Added pet {}", pet);
                return ResponseEntity.ok(Map.of("success", true, "pet", pet, "message", "Pet added"));
            }
            case "update": {
                if (request.id == null || request.id < 1 || request.name == null || request.category == null || request.status == null) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Missing or invalid fields for update");
                }

                // Find pet by id to get technicalId
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", request.id));
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
                ArrayNode filteredItems = filteredItemsFuture.get();
                if (filteredItems.isEmpty()) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
                }
                ObjectNode storedPetNode = (ObjectNode) filteredItems.get(0);
                if (!storedPetNode.hasNonNull("technicalId")) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Stored entity missing technicalId");
                }
                UUID technicalId;
                try {
                    technicalId = UUID.fromString(storedPetNode.get("technicalId").asText());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Invalid technicalId format");
                }

                // Update fields with trimmed, normalized values
                storedPetNode.put("name", request.name.trim());
                storedPetNode.put("category", request.category.trim());
                String status = request.status.toLowerCase(Locale.ROOT);
                if (!status.equals("available") && !status.equals("pending") && !status.equals("sold")) {
                    status = "available"; // fallback default
                }
                storedPetNode.put("status", status);

                // Update synchronously (entityService.updateItem currently no workflow support)
                CompletableFuture<UUID> updatedItemId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, storedPetNode);
                updatedItemId.get();

                Pet pet = objectMapper.convertValue(storedPetNode, Pet.class);
                logger.info("Updated pet {}", pet);
                return ResponseEntity.ok(Map.of("success", true, "pet", pet, "message", "Pet updated"));
            }
            default:
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported action");
        }
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchPets(@Valid @RequestBody SearchRequest request) throws ExecutionException, InterruptedException {
        logger.info("POST /entity/pets/search filters={}", request);
        List<Condition> conditions = new ArrayList<>();
        if (request.status != null) {
            conditions.add(Condition.of("$.status", "IEQUALS", request.status));
        }
        if (request.category != null) {
            conditions.add(Condition.of("$.category", "ICONTAINS", request.category));
        }
        if (request.nameContains != null) {
            conditions.add(Condition.of("$.name", "ICONTAINS", request.nameContains));
        }
        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode items = itemsFuture.get();

        List<Pet> results = new ArrayList<>();
        for (JsonNode node : items) {
            Pet pet = objectMapper.convertValue(node, Pet.class);
            results.add(pet);
        }
        return ResponseEntity.ok(Map.of("results", results));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPetById(@PathVariable @Min(1) Long id) throws ExecutionException, InterruptedException {
        logger.info("GET /entity/pets/{}", id);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
        ArrayNode filteredItems = filteredItemsFuture.get();
        if (filteredItems.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        ObjectNode petNode = (ObjectNode) filteredItems.get(0);
        Pet pet = objectMapper.convertValue(petNode, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @GetMapping
    public ResponseEntity<?> listAllPets() throws ExecutionException, InterruptedException {
        logger.info("GET /entity/pets");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();

        List<Pet> pets = new ArrayList<>();
        for (JsonNode node : items) {
            Pet pet = objectMapper.convertValue(node, Pet.class);
            pets.add(pet);
        }
        return ResponseEntity.ok(pets);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleError(ResponseStatusException ex) {
        logger.error("Error {}: {}", ex.getStatusCode(), ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }
}