Certainly! Moving asynchronous and entity state modification logic into the workflow function (`process{EntityName}`) is a great idea. It centralizes entity-related business logic, keeps controllers lean, and leverages the asynchronous capabilities of the workflow function for side tasks like validation, enrichment, or fetching supplemental data.

---

### What will be moved to the workflow function?

1. Any logic that modifies the entity data before persistence.
2. Calls that fetch supplementary data or perform async tasks related to entity enrichment.
3. Logic that deals with setting default values or calculated fields.
4. Avoid any `add/update/delete` on the same entity model inside the workflow to prevent infinite recursion.

---

### What will remain in the controller?

- Validation of the request payload.
- Handling request/response mapping.
- Calling the service with the workflow function.
- Handling the HTTP response.

---

### How to handle the fact that the entity in the workflow is now an `ObjectNode`?

- Workflow function signature changes to accept `ObjectNode` instead of the POJO.
- Convert or manipulate data in `ObjectNode` directly.
- Use Jackson or `ObjectNode` API to modify fields.

---

### Below is the updated Java controller class with:

- Workflow functions for **Pet** entity moved all async or transformation logic inside.
- Controller now just passes data and workflow function.
- Workflow function is async returning `CompletableFuture<ObjectNode>` (to demonstrate async capability).
- Example of fetching supplementary data in workflow.
- Usage of `ObjectNode` for entity state mutation.

---

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda/entity/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private static final String ENTITY_NAME = "pet";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/search")
    public ResponseEntity<SearchPetsResponse> searchPets(@RequestBody @Valid SearchPetsRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received searchPets request: type='{}', status='{}'", request.getType(), request.getStatus());

        if ((request.getStatus() == null || request.getStatus().isBlank()) && (request.getType() == null || request.getType().isBlank())) {
            logger.info("No search criteria provided, returning empty pet list");
            return ResponseEntity.ok(new SearchPetsResponse(Collections.emptyList()));
        }

        List<Condition> conditions = new ArrayList<>();
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            conditions.add(Condition.of("$.status", "EQUALS", request.getStatus()));
        }
        if (request.getType() != null && !request.getType().isBlank()) {
            conditions.add(Condition.of("$.type", "IEQUALS", request.getType()));
        }
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, searchCondition);
        ArrayNode filteredItems = filteredItemsFuture.get();

        List<Pet> pets = new ArrayList<>();
        filteredItems.forEach(node -> {
            Pet pet = mapObjectNodeToPet((ObjectNode) node);
            if (pet != null) {
                pets.add(pet);
            }
        });
        logger.info("Search returned {} pets", pets.size());
        return ResponseEntity.ok(new SearchPetsResponse(pets));
    }

    @GetMapping
    public ResponseEntity<SearchPetsResponse> getPets(@Valid @ModelAttribute PetsQuery query) throws ExecutionException, InterruptedException {
        logger.info("Received getPets request: type='{}', status='{}'", query.getType(), query.getStatus());

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();

        List<Pet> filtered = new ArrayList<>();
        items.forEach(node -> {
            Pet pet = mapObjectNodeToPet((ObjectNode) node);
            if (pet != null) {
                boolean matches = true;
                if (query.getType() != null) matches &= query.getType().equalsIgnoreCase(pet.getType());
                if (query.getStatus() != null) matches &= query.getStatus().equalsIgnoreCase(pet.getStatus());
                if (matches) {
                    filtered.add(pet);
                }
            }
        });

        logger.info("Returning {} pets from entity service", filtered.size());
        return ResponseEntity.ok(new SearchPetsResponse(filtered));
    }

    @PostMapping
    public ResponseEntity<CreatePetResponse> addPet(@RequestBody @Valid CreatePetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received addPet request: name='{}', type='{}', status='{}'", request.getName(), request.getType(), request.getStatus());

        // Construct ObjectNode for pet entity
        ObjectNode petNode = entityService.getObjectMapper().createObjectNode();
        petNode.put("name", request.getName());
        petNode.put("type", request.getType());
        petNode.put("status", request.getStatus());
        petNode.putArray("photoUrls").addAll(request.getPhotoUrls().stream().map(petNode::textNode).collect(Collectors.toList()));
        // favorite is optional, do not set here; will be set in workflow if needed

        // Pass the workflow function processPet as parameter to addItem
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode, this::processPet);
        UUID technicalId = idFuture.get();

        logger.info("Pet created with technicalId={}", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatePetResponse(technicalId, "Pet created successfully"));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<FavoriteResponse> markFavorite(@PathVariable("id") @NotNull UUID id,
                                                         @RequestBody @Valid FavoriteRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received markFavorite request for technicalId={} favorite={}", id, request.getFavorite());

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode existingItem = itemFuture.get();
        if (existingItem == null || existingItem.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        // Update favorite field directly in ObjectNode
        existingItem.put("favorite", request.getFavorite());

        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, existingItem);
        updatedIdFuture.get();

        logger.info("Favorite status updated for technicalId={} to {}", id, request.getFavorite());
        return ResponseEntity.ok(new FavoriteResponse(id, request.getFavorite(), "Favorite status updated"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") @NotNull UUID id) throws ExecutionException, InterruptedException {
        logger.info("Received getPetById request for technicalId={}", id);

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        Pet pet = mapObjectNodeToPet(item);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        return ResponseEntity.ok(pet);
    }

    /**
     * Workflow function with prefix 'process' followed by entity name 'Pet'.
     * This function is applied to the entity asynchronously before persistence.
     * It takes the entity data as the only argument and returns it back.
     * You can modify the entity here (e.g. set default values, validate, enrich).
     * You can get and add entities of different entityModels.
     * You must NOT call add/update/delete on the same entityModel ("pet").
     */
    private CompletableFuture<ObjectNode> processPet(ObjectNode pet) {
        logger.info("Executing processPet workflow for pet: name='{}', type='{}', status='{}'",
                pet.path("name").asText(""), pet.path("type").asText(""), pet.path("status").asText(""));

        // Example: Ensure favorite is set to false if missing or null
        if (!pet.hasNonNull("favorite")) {
            pet.put("favorite", false);
        }

        // Example: Add or enrich pet info with external supplementary data asynchronously
        // For demonstration, let's say we fetch some "petCategory" entity by type
        String petType = pet.path("type").asText(null);
        if (petType != null) {
            // Fetch supplementary data asynchronously
            return entityService.getItemsByCondition("petCategory", ENTITY_VERSION,
                    SearchConditionRequest.group("AND",
                            Condition.of("$.type", "EQUALS", petType)))
                    .thenApply(categories -> {
                        // If found, add first category's name as "categoryName" to pet
                        if (categories != null && categories.size() > 0) {
                            JsonNode firstCategory = categories.get(0);
                            String categoryName = firstCategory.path("name").asText(null);
                            if (categoryName != null) {
                                pet.put("categoryName", categoryName);
                            }
                        }
                        // Return modified pet node
                        return pet;
                    });
        } else {
            // No petType, return completed future immediately
            return CompletableFuture.completedFuture(pet);
        }
    }

    private Pet mapObjectNodeToPet(ObjectNode node) {
        try {
            UUID technicalId = node.has("technicalId") && !node.get("technicalId").isNull() ? UUID.fromString(node.get("technicalId").asText()) : null;
            String name = node.has("name") && !node.get("name").isNull() ? node.get("name").asText() : null;
            String status = node.has("status") && !node.get("status").isNull() ? node.get("status").asText() : null;
            String type = node.has("type") && !node.get("type").isNull() ? node.get("type").asText() : null;
            List<String> photoUrls = new ArrayList<>();
            if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
                for (JsonNode urlNode : node.get("photoUrls")) {
                    photoUrls.add(urlNode.asText());
                }
            }
            Boolean favorite = node.has("favorite") && !node.get("favorite").isNull() ? node.get("favorite").asBoolean() : false;
            // Optional supplementary field "categoryName"
            String categoryName = node.has("categoryName") && !node.get("categoryName").isNull() ? node.get("categoryName").asText() : null;

            if (technicalId == null || name == null) return null;

            Pet pet = new Pet(technicalId, name, type, status, photoUrls, favorite);
            pet.setCategoryName(categoryName);
            return pet;
        } catch (Exception e) {
            logger.error("Failed to map ObjectNode to Pet", e);
            return null;
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

    @Data
    public static class PetsQuery {
        @Size(max = 50)
        private String type;
        @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    public static class SearchPetsRequest {
        @Size(max = 50)
        private String type;
        @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class SearchPetsResponse {
        private List<Pet> pets;
    }

    @Data
    public static class CreatePetRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 100)
        private String type;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> photoUrls;
    }

    @Data
    @AllArgsConstructor
    public static class CreatePetResponse {
        @NotNull
        private UUID id;
        @NotBlank
        private String message;
    }

    @Data
    public static class FavoriteRequest {
        @NotNull
        private Boolean favorite;
    }

    @Data
    @AllArgsConstructor
    public static class FavoriteResponse {
        private UUID id;
        private Boolean favorite;
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Pet {
        private UUID id; // mapped from technicalId
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
        private Boolean favorite;
        private String categoryName; // supplementary field added in workflow
    }
}
```

---

### Summary of changes:

- The `addPet()` controller creates an `ObjectNode` representing the pet entity and passes it to `entityService.addItem` **along with** the workflow function `processPet`.
- The `processPet(ObjectNode pet)` workflow function:
  - Sets default `favorite` if missing.
  - Asynchronously fetches supplementary `"petCategory"` entity by the pet's type.
  - Adds `"categoryName"` to the pet entity if found.
- The controller no longer contains any enrichment or async calls for supplementary data.
- The controller simply handles input/output, validation, and delegates entity state mutations and async enrichment to the workflow function.
- Mapping from `ObjectNode` to `Pet` POJO now includes a supplementary `categoryName` field.

---

This approach reduces controller complexity, centralizes async data enrichment and entity state mutation before persistence, and respects the contract that you cannot modify the same entity model inside the workflow except by mutating the provided `ObjectNode`.

If you have other async tasks related to entity mutation or enrichment, consider moving them similarly into the workflow function.

---

If you want me to do the same for update or other mutations, or handle other async controller logic, just ask!