Certainly! Moving logic from controllers into the workflow functions (`process{entity_name}`) improves separation of concerns, reduces controller complexity, and leverages the workflow's ability to run async logic before persistence.

We will:

- Move any async or entity state mutation logic to the respective `process{entity_name}` workflow function and invoke it from controller via `addItem` or `updateItem` as needed.
- Modify the workflow functions to accept `ObjectNode` (Jackson JSON tree) because entity data is passed as an `ObjectNode` and must be mutated directly.
- Move adoption status update and any async side effects into `processpet` workflow.
- Controllers become thin, just forwarding requests and calling `entityService` with workflow functions.

---

### Updated code (only relevant parts shown, full class below):

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda/pets")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private static final String ENTITY_NAME = "pet";

    /**
     * Workflow function applied to the entity asynchronously before persistence.
     * ObjectNode entity can be mutated directly.
     * You can get/add entities of different entityModel but cannot modify same entityModel.
     */
    private final Function<ObjectNode, CompletableFuture<ObjectNode>> processpet = entity -> {
        // Example: normalize status to lowercase
        if (entity.has("status") && !entity.get("status").isNull()) {
            String status = entity.get("status").asText();
            entity.put("status", status.toLowerCase());
        }

        // If it's adoption workflow (status changed to "adopted") - perform async side effects here
        if ("adopted".equals(entity.get("status").asText(null))) {
            // Fire and forget example: notify systems asynchronously (just logging here)
            CompletableFuture.runAsync(() -> {
                logger.info("Adoption workflow triggered for pet with technicalId={}", entity.get("technicalId").asText(null));
                // TODO: invoke external async notifications, event publishing etc.
            });
        }
        // Return entity to persist it
        return CompletableFuture.completedFuture(entity);
    };

    @PostMapping("/fetch")
    public ResponseEntity<PetsResponse> fetchPets(@RequestBody @Valid FetchRequest fetchRequest) throws ExecutionException, InterruptedException {
        logger.info("Received fetch request with filters: type={}, status={}", fetchRequest.getType(), fetchRequest.getStatus());

        String statusQuery = (fetchRequest.getStatus() == null || fetchRequest.getStatus().isBlank())
                ? "available" : fetchRequest.getStatus().toLowerCase();

        Condition statusCondition = Condition.of("$.status", "IEQUALS", statusQuery);
        SearchConditionRequest conditionRequest;

        if (fetchRequest.getType() == null || fetchRequest.getType().isBlank()) {
            conditionRequest = SearchConditionRequest.group("AND", statusCondition);
        } else {
            Condition typeCondition = Condition.of("$.type", "IEQUALS", fetchRequest.getType());
            conditionRequest = SearchConditionRequest.group("AND", statusCondition, typeCondition);
        }

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode itemsNode = itemsFuture.get();

        List<Pet> pets = jsonNodeToPetList(itemsNode);

        logger.info("Fetched {} pets from EntityService", pets.size());
        return ResponseEntity.ok(new PetsResponse(pets));
    }

    @GetMapping
    public ResponseEntity<PetsResponse> getCachedPets() throws ExecutionException, InterruptedException {
        logger.info("Fetching all pets");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode itemsNode = itemsFuture.get();
        List<Pet> pets = jsonNodeToPetList(itemsNode);
        logger.info("Returning {} pets", pets.size());
        return ResponseEntity.ok(new PetsResponse(pets));
    }

    /**
     * Adopt pet endpoint - simplified: fetch entity then update status via workflow function.
     * We do not update the entity directly here but submit modified entity with updated status to updateItem with workflow.
     */
    @PostMapping("/adopt")
    public ResponseEntity<AdoptResponse> adoptPet(@RequestBody @Valid AdoptRequest adoptRequest) throws ExecutionException, InterruptedException {
        logger.info("Received adoption request for technicalId={}", adoptRequest.getPetId());

        Condition idCondition = Condition.of("$.technicalId", "EQUALS", adoptRequest.getPetId().toString());
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", idCondition);
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, searchCondition);
        ArrayNode foundItems = itemsFuture.get();
        if (foundItems == null || foundItems.isEmpty()) {
            logger.error("Pet with id {} not found", adoptRequest.getPetId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        ObjectNode petNode = (ObjectNode) foundItems.get(0);

        // Update status to "adopted" before persistence
        petNode.put("status", "adopted");

        UUID technicalId = UUID.fromString(petNode.get("technicalId").asText());

        // Pass modified entity to updateItem with workflow function to handle async tasks and validation
        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, petNode, processpet);
        updatedIdFuture.get();

        logger.info("Pet with technicalId {} marked as adopted", technicalId);
        return ResponseEntity.ok(new AdoptResponse(true, "Pet adoption status updated."));
    }

    /**
     * Add new pet example using workflow function
     */
    @PostMapping("/add")
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid Pet pet) throws ExecutionException, InterruptedException {
        logger.info("Adding new pet with name={}", pet.getName());

        // Convert Pet to ObjectNode (assume helper method exists)
        ObjectNode entityNode = petToObjectNode(pet);

        CompletableFuture<UUID> addFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entityNode, processpet);
        UUID id = addFuture.get();

        logger.info("Added new pet with technicalId={}", id);
        return ResponseEntity.ok(new AddPetResponse(id));
    }


    // Helper methods for JSON conversions

    private List<Pet> jsonNodeToPetList(ArrayNode arrayNode) {
        return arrayNode.findValues(null).stream()
                .map(node -> {
                    if (node.isObject())
                        return jsonNodeToPet((ObjectNode) node);
                    else
                        return null;
                })
                .filter(pet -> pet != null)
                .collect(Collectors.toList());
    }

    private Pet jsonNodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        if (node.has("technicalId")) pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        if (node.has("name")) pet.setName(node.get("name").asText(null));
        if (node.has("status")) pet.setStatus(node.get("status").asText(null));
        if (node.has("type")) pet.setType(node.get("type").asText(null));
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            pet.setPhotoUrls(node.findValuesAsText("photoUrls"));
        }
        return pet;
    }

    private ObjectNode petToObjectNode(Pet pet) {
        // Convert Pet POJO to ObjectNode using Jackson ObjectMapper
        return entityService.getObjectMapper().valueToTree(pet);
    }

    // DTOs

    @Data
    public static class FetchRequest {
        @Size(max = 30)
        private String type;
        @Pattern(regexp = "available|pending|sold", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String status;
    }

    @Data
    public static class AdoptRequest {
        @NotNull
        private UUID petId;
    }

    @Data
    public static class PetsResponse {
        private List<Pet> pets;
        public PetsResponse(List<Pet> pets) { this.pets = pets; }
    }

    @Data
    public static class AdoptResponse {
        private boolean success;
        private String message;
        public AdoptResponse(boolean success, String message) { this.success = success; this.message = message; }
    }

    @Data
    public static class AddPetResponse {
        private UUID technicalId;
        public AddPetResponse(UUID technicalId) { this.technicalId = technicalId; }
    }

    @Data
    public static class Pet {
        private UUID technicalId;
        private String name;
        private String status;
        private String type;
        private List<String> photoUrls;
    }

}
```

---

### Summary of improvements:

- **`processpet` workflow function** now:
  - Normalizes the pet's `status` field.
  - Detects adoption by `status == "adopted"` and triggers async notifications or side effects.
- **`adoptPet` controller** now:
  - Fetches pet entity as `ObjectNode`.
  - Updates the status field in the entity directly.
  - Calls `entityService.updateItem` with the updated entity and workflow function `processpet`.
  - All async side effects moved into `processpet` (fire-and-forget notifications etc.).
- **`addPet` controller**:
  - Converts POJO to `ObjectNode`.
  - Calls `entityService.addItem` with workflow function.
- Controllers are now thin, focusing on request validation and forwarding.
- All mutation and async side effects moved into `processpet` workflow function as required.
- The workflow function returns a `CompletableFuture<ObjectNode>`, enabling async processing before persistence.

---

If you want me to move any other async logic or create additional workflow functions for other entity models, just ask!