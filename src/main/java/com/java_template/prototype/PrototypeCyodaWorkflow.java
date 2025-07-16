Certainly! Moving asynchronous tasks and entity state modifications into the workflow function (`processPet`) will clean up the controller and centralize entity-related logic, making the controller primarily responsible for request validation, routing, and response handling.

---

### What can and will be moved to `processPet`:

- Any async external API calls that enrich or initialize the entity data before persistence.
- Any defaulting or modification of entity fields.
- Any fetching or creating of supplementary entities (different `entityModel`).
- Avoid calling `addItem/updateItem/deleteItem` on the same entity model inside workflow to prevent recursion.

---

### What remains in the controller:

- Validating input.
- Converting request DTOs to the entity model (`ObjectNode`).
- Calling `entityService` methods with the `processPet` workflow function.
- Handling exceptions and returning responses.

---

### Implementation plan:

- Convert `Pet` entity to `ObjectNode` inside controller before calling `addItem/updateItem`.
- Move external API fetch (petstore API) into `processPet` workflow, if the entity is new or missing some data.
- Move setting defaults like `description` into `processPet`.
- Remove async waits from controller; controller just waits for the final future from `entityService`.
- Convert entity from `ObjectNode` back to `Pet` only after persistence.

---

### Here is the updated Java code with the above principles:

```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "pet";
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    @Data
    public static class PetRequest {
        private String petId; // optional for new pets
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 100)
        private String category;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(max = 255)
        private String description;
    }

    @Data
    public static class PetDeleteRequest {
        @NotBlank
        private String petId;
    }

    @Data
    public static class PetSearchRequest {
        @Size(max = 100)
        private String name;
        @Size(max = 100)
        private String category;
        @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    public static class PetResponse {
        private boolean success;
        private Pet pet;
    }

    @Data
    public static class DeleteResponse {
        private boolean success;
        private String message;
    }

    @Data
    public static class Pet {
        private UUID technicalId; // internal id from entityService
        private String petId; // string form of technicalId
        private String name;
        private String category;
        private String status;
        private String description;
    }

    /**
     * The workflow function applied asynchronously before persistence.
     * It enriches the entity, fetches external data if necessary,
     * sets defaults, and can interact with other entity models.
     *
     * @param entity ObjectNode representing the pet entity to be persisted
     * @return CompletableFuture<ObjectNode> asynchronously completed with the possibly modified entity
     */
    private CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Workflow processPet started for entity: {}", entity);

                // Ensure description default
                if (!entity.hasNonNull("description") || entity.get("description").asText().isBlank()) {
                    entity.put("description", "A lovely pet!");
                }

                // If this is a new entity (no technicalId) or missing key data, fetch external data
                boolean needsExternalFetch = !entity.hasNonNull("technicalId");
                if (!needsExternalFetch) {
                    // Check if entity is missing essential fields (name/category/status)
                    String name = entity.path("name").asText("");
                    String category = entity.path("category").asText("");
                    String status = entity.path("status").asText("");
                    if (name.isBlank() || category.isBlank() || status.isBlank()) {
                        needsExternalFetch = true;
                    }
                }

                if (needsExternalFetch) {
                    // Try to fetch missing data from external API based on petId (which is string version of technicalId)
                    String petIdStr = entity.hasNonNull("petId") ? entity.get("petId").asText() : null;
                    if (petIdStr != null && !petIdStr.isBlank()) {
                        try {
                            String url = PETSTORE_API_BASE + "/" + petIdStr;
                            String response = restTemplate.getForObject(url, String.class);
                            if (response != null) {
                                JsonNode externalPetJson = objectMapper.readTree(response);
                                // Map external data to our entity fields if absent or blank
                                if (!entity.hasNonNull("name") || entity.get("name").asText().isBlank()) {
                                    entity.put("name", externalPetJson.path("name").asText(""));
                                }
                                if (!entity.hasNonNull("category") || entity.get("category").asText().isBlank()) {
                                    // category might be nested, example: category.name
                                    String cat = externalPetJson.path("category").path("name").asText("");
                                    entity.put("category", cat);
                                }
                                if (!entity.hasNonNull("status") || entity.get("status").asText().isBlank()) {
                                    entity.put("status", externalPetJson.path("status").asText(""));
                                }
                                if (!entity.hasNonNull("description") || entity.get("description").asText().isBlank()) {
                                    entity.put("description", "A lovely pet!");
                                }
                            }
                        } catch (Exception ex) {
                            logger.warn("Failed to fetch external data for petId={} in workflow: {}", petIdStr, ex.toString());
                            // We do not fail workflow on external API failure, just log warning
                        }
                    }
                }

                // Example: We could also add/get supplementary entities here if needed
                // e.g., entityService.getItems("otherEntityModel", ...), but we must not add/update/delete "pet" here.

                logger.info("Workflow processPet finished for entity: {}", entity);
                return entity;
            } catch (Exception e) {
                logger.error("Error in workflow processPet", e);
                // In case of error, just return entity unchanged to avoid blocking persistence
                return entity;
            }
        });
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PetResponse addOrUpdatePet(@RequestBody @Valid PetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received addOrUpdatePet request: {}", request);

        ObjectNode petNode = objectMapper.createObjectNode();

        UUID technicalId = null;
        if (request.getPetId() != null && !request.getPetId().isBlank()) {
            try {
                technicalId = UUID.fromString(request.getPetId());
                petNode.put("technicalId", technicalId.toString());
                petNode.put("petId", technicalId.toString());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid petId format");
            }
        } else {
            // No petId means new entity, no technicalId in node
        }

        // Set fields from request
        petNode.put("name", request.getName());
        petNode.put("category", request.getCategory());
        petNode.put("status", request.getStatus());

        if (request.getDescription() != null) {
            petNode.put("description", request.getDescription());
        }

        CompletableFuture<UUID> resultFuture;

        if (technicalId == null) {
            // New entity - add with workflow
            resultFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode, this::processPet);
        } else {
            // Existing entity - update with workflow
            resultFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, petNode, this::processPet);
        }

        UUID persistedId = resultFuture.get();

        // Retrieve persisted entity for response
        CompletableFuture<ObjectNode> persistedNodeFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, persistedId);
        ObjectNode persistedNode = persistedNodeFuture.get();

        Pet pet = convertObjectNodeToPet(persistedNode);

        PetResponse response = new PetResponse();
        response.setSuccess(true);
        response.setPet(pet);
        return response;
    }

    @GetMapping
    public Collection<Pet> getPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode itemsNode = itemsFuture.get();
        List<Pet> pets = new ArrayList<>();
        if (itemsNode != null) {
            for (JsonNode node : itemsNode) {
                pets.add(convertObjectNodeToPet((ObjectNode) node));
            }
        }
        logger.info("Retrieving all pets, count={}", pets.size());
        return pets;
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Collection<Pet> searchPets(@RequestBody @Valid PetSearchRequest request) throws ExecutionException, InterruptedException {
        logger.info("Searching pets with criteria: {}", request);
        List<Condition> conditions = new ArrayList<>();
        if (request.getName() != null && !request.getName().isBlank()) {
            conditions.add(Condition.of("$.name", "ICONTAINS", request.getName()));
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            conditions.add(Condition.of("$.category", "IEQUALS", request.getCategory()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            conditions.add(Condition.of("$.status", "IEQUALS", request.getStatus()));
        }

        SearchConditionRequest conditionRequest;
        if (conditions.isEmpty()) {
            conditionRequest = null;
        } else {
            conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
        }

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode filteredItems = filteredItemsFuture.get();
        List<Pet> results = new ArrayList<>();
        if (filteredItems != null) {
            for (JsonNode node : filteredItems) {
                results.add(convertObjectNodeToPet((ObjectNode) node));
            }
        }
        logger.info("Search returned {} results", results.size());
        return results;
    }

    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeleteResponse deletePet(@RequestBody @Valid PetDeleteRequest request) throws ExecutionException, InterruptedException {
        logger.info("Deleting pet with petId: {}", request.getPetId());
        UUID technicalId;
        try {
            technicalId = UUID.fromString(request.getPetId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid petId format");
        }
        CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
        UUID deletedId = deletedIdFuture.get();
        DeleteResponse response = new DeleteResponse();
        if (deletedId != null) {
            response.setSuccess(true);
            response.setMessage("Pet deleted successfully");
            logger.info("Pet deleted: {}", request.getPetId());
        } else {
            response.setSuccess(false);
            response.setMessage("Pet not found");
            logger.warn("Pet to delete not found: {}", request.getPetId());
        }
        return response;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return errorBody;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString());
        errorBody.put("message", "Internal server error");
        return errorBody;
    }

    private Pet convertObjectNodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        if (node.hasNonNull("technicalId")) {
            pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
            pet.setPetId(pet.getTechnicalId().toString());
        }
        if (node.hasNonNull("name")) {
            pet.setName(node.get("name").asText());
        }
        if (node.hasNonNull("category")) {
            pet.setCategory(node.get("category").asText());
        }
        if (node.hasNonNull("status")) {
            pet.setStatus(node.get("status").asText());
        }
        if (node.hasNonNull("description")) {
            pet.setDescription(node.get("description").asText());
        }
        return pet;
    }
}
```

---

### Summary of key changes:

- **Moved external API fetch logic into `processPet`** workflow function.
- **Moved default description setting into `processPet`.**
- Controller now converts the request into an `ObjectNode` and passes it to `addItem` or `updateItem` with `processPet` workflow.
- Controller no longer fetches or updates entities itself asynchronously before calling `entityService`.
- Controller retrieves the persisted entity after the operation completes for returning response.
- `processPet` returns `CompletableFuture<ObjectNode>`, fully asynchronous, allowing async calls inside the workflow.
- `processPet` does not call `addItem/updateItem/deleteItem` on the `"pet"` entity model, avoiding recursion.
- `processPet` logs warnings on external API fetch failures but does not fail the workflow.
- Controller focuses only on validation, DTO mapping, and response formatting.

---

This will make your code more robust, maintainable, and aligned with the intended architecture where workflows manage async, enrichment, and entity state before persistence.