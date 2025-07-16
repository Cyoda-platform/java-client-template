```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/entity/pets")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "pets";
    private static final String EXTERNAL_PETSTORE_BASE = "https://petstore.swagger.io/v2/pet";

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Workflow function to process Pet entity before persistence.
     * You can modify the pet data here or add/get other entities (not of pets).
     */
    private Pet processPets(Pet pet) {
        // Example workflow: Just return the pet as is.
        // You can add logic here to modify pet before saving.
        return pet;
    }

    @PostMapping
    public ResponseEntity<PetResponse> addOrUpdatePet(@RequestBody @Valid AddOrUpdatePetRequest request) throws ExecutionException, InterruptedException {
        logger.info("addOrUpdatePet source={}", request.getSource());
        if ("external".equalsIgnoreCase(request.getSource())) {
            if (request.getPetId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId required for external source");
            }
            try {
                String url = EXTERNAL_PETSTORE_BASE + "/" + request.getPetId();
                logger.info("Fetching external pet from {}", url);
                String json = restTemplate.getForObject(url, String.class);
                JsonNode node = objectMapper.readTree(json);
                long id = node.path("id").asLong(-1);
                if (id < 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "External pet not found");
                String name = node.path("name").asText("");
                String status = node.path("status").asText("");
                JsonNode cat = node.path("category");
                String type = cat.isMissingNode() ? "unknown" : cat.path("name").asText("unknown");
                int age = 0; // TODO: map age if available

                Pet pet = new Pet(id, name, type, age, status);
                // Convert Pet to ObjectNode for add/update
                ObjectNode petNode = objectMapper.valueToTree(pet);
                // Remove id field, use technicalId only when retrieved
                petNode.remove("id");

                // Check if pet with this id exists
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", id));
                CompletableFuture<ArrayNode> existingItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
                ArrayNode existingItems = existingItemsFuture.get();
                UUID techId = null;
                if (existingItems.size() > 0) {
                    // update existing
                    ObjectNode existing = (ObjectNode) existingItems.get(0);
                    techId = UUID.fromString(existing.get("technicalId").asText());
                    CompletableFuture<UUID> updatedId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, techId, pet);
                    updatedId.get();
                } else {
                    // add new with workflow function
                    CompletableFuture<UUID> addedId = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet, this::processPets);
                    techId = addedId.get();
                }
                return ResponseEntity.ok(new PetResponse(true, pet));
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                logger.error("external fetch error", e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "External fetch failed");
            }
        } else if ("internal".equalsIgnoreCase(request.getSource())) {
            if (request.getName() == null || request.getType() == null || request.getStatus() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name,type,status required for internal source");
            }
            Long petId = request.getPetId();
            Pet pet;
            if (petId == null) {
                // Adding new pet without id - just add and get technicalId
                pet = new Pet(0L, request.getName(), request.getType(), request.getAge() != null ? request.getAge() : 0, request.getStatus());
                CompletableFuture<UUID> addedId = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet, this::processPets);
                addedId.get();
            } else {
                // Updating existing pet with id - need to find technicalId first
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", petId));
                CompletableFuture<ArrayNode> existingItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
                ArrayNode existingItems = existingItemsFuture.get();
                if (existingItems.size() == 0) {
                    // New pet with specified id
                    pet = new Pet(petId, request.getName(), request.getType(), request.getAge() != null ? request.getAge() : 0, request.getStatus());
                    CompletableFuture<UUID> addedId = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet, this::processPets);
                    addedId.get();
                } else {
                    ObjectNode existing = (ObjectNode) existingItems.get(0);
                    UUID techId = UUID.fromString(existing.get("technicalId").asText());
                    pet = new Pet(petId, request.getName(), request.getType(), request.getAge() != null ? request.getAge() : 0, request.getStatus());
                    CompletableFuture<UUID> updatedId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, techId, pet);
                    updatedId.get();
                }
            }
            logger.info("Stored internal pet id={}", pet.getId());
            return ResponseEntity.ok(new PetResponse(true, pet));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source");
        }
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody @Valid SearchRequest request) throws ExecutionException, InterruptedException {
        logger.info("searchPets type={},status={},minAge={},maxAge={}",
                request.getType(), request.getStatus(), request.getMinAge(), request.getMaxAge());

        List<Condition> conditions = new ArrayList<>();
        if (request.getType() != null) {
            conditions.add(Condition.of("$.type", "IEQUALS", request.getType()));
        }
        if (request.getStatus() != null) {
            conditions.add(Condition.of("$.status", "IEQUALS", request.getStatus()));
        }
        if (request.getMinAge() != null) {
            conditions.add(Condition.of("$.age", "GREATER_OR_EQUAL", request.getMinAge()));
        }
        if (request.getMaxAge() != null) {
            conditions.add(Condition.of("$.age", "LESS_OR_EQUAL", request.getMaxAge()));
        }

        SearchConditionRequest conditionRequest = conditions.isEmpty() ? null : SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
        CompletableFuture<ArrayNode> resultsFuture = conditionRequest == null
                ? entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                : entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode resultsNode = resultsFuture.get();

        List<Pet> results = new ArrayList<>();
        for (JsonNode node : resultsNode) {
            Pet pet = mapNodeToPet(node);
            results.add(pet);
        }
        return ResponseEntity.ok(new SearchResponse(results));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") @Min(1) long id) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.get();
        if (items.size() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = mapNodeToPet(items.get(0));
        return ResponseEntity.ok(pet);
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getAllPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        List<Pet> pets = new ArrayList<>();
        for (JsonNode node : items) {
            pets.add(mapNodeToPet(node));
        }
        return ResponseEntity.ok(pets);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleError(ResponseStatusException ex) {
        logger.error("error {} {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    private Pet mapNodeToPet(JsonNode node) {
        long id = node.path("id").asLong(0);
        if (id == 0) {
            // id might be missing, try to get from fields other than technicalId
            // but legacy pet id is required, so fallback to 0 if missing
        }
        String name = node.path("name").asText("");
        String type = node.path("type").asText("");
        int age = node.path("age").asInt(0);
        String status = node.path("status").asText("");
        return new Pet(id, name, type, age, status);
    }

    @Data
    public static class AddOrUpdatePetRequest {
        @NotBlank
        private String source; // external or internal
        private Long petId;
        @Size(min = 1)
        private String name;
        @Size(min = 1)
        private String type;
        @Min(0)
        private Integer age;
        @Size(min = 1)
        private String status;
    }

    @Data
    public static class SearchRequest {
        @Size(min = 1)
        private String type;
        @Size(min = 1)
        private String status;
        @Min(0)
        private Integer minAge;
        @Min(0)
        private Integer maxAge;
    }

    @Data
    public static class PetResponse {
        private final boolean success;
        private final Pet pet;
    }

    @Data
    public static class SearchResponse {
        private final List<Pet> results;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }

    @Data
    public static class Pet {
        private final long id;
        private final String name;
        private final String type;
        private final int age;
        private final String status;
    }

}
```
---

### Explanation of changes:
- Added a private workflow method `processPets(Pet pet)` that matches the required naming convention (`process` + entity name `"pets"`).
- This method simply returns the pet as is, but you can add any custom logic to modify the pet before persistence.
- Updated all calls to `entityService.addItem(...)` by adding the workflow function as the last argument: `entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet, this::processPets)`.
- No changes to updateItem calls since the change only affects addItem.
- The rest of the code remains unchanged to avoid breaking existing logic.