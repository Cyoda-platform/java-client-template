```java
package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.service.EntityService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "pet";

    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow function: processPet, per new addItem signature requirement
    private Pet processPet(Pet pet) {
        // Example processing logic: you can modify pet state here if needed before persistence
        // For demonstration, just return the pet as is.
        return pet;
    }

    // still calling external Petstore API on query, then filtering and storing in entityService
    @PostMapping("/query")
    public ResponseEntity<PetsResponse> queryPets(@RequestBody @Valid PetQueryRequest queryRequest) throws Exception {
        logger.info("Received pet query request: {}", queryRequest);
        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=";
        if (queryRequest.getStatus() != null) {
            url += queryRequest.getStatus();
        } else {
            url += "available,pending,sold";
        }
        logger.info("Calling external Petstore API: {}", url);
        String responseJson = restTemplate.getForObject(url, String.class);
        JsonNode rootNode = JsonUtils.parseJson(responseJson);
        List<Pet> filteredPets = new ArrayList<>();
        if (rootNode.isArray()) {
            for (JsonNode node : rootNode) {
                Pet pet = mapJsonNodeToPet(node);
                if (matchesQuery(pet, queryRequest)) {
                    filteredPets.add(pet);
                }
            }
        } else {
            logger.warn("Unexpected response format from Petstore API");
        }
        // add filtered pets to entityService using updated addItems with workflow function
        // Note: addItems presumably also needs workflow; assuming addItems overload exists similarly.
        // If not, you can add them one by one with workflow.
        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (Pet pet : filteredPets) {
            CompletableFuture<UUID> future = entityService.addItem(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    pet,
                    this::processPet);
            futures.add(future);
        }
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allDone.join();

        return ResponseEntity.ok(new PetsResponse(filteredPets));
    }

    @GetMapping
    public ResponseEntity<PetsResponse> getAllPets() throws Exception {
        logger.info("Fetching all pets from entityService");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                ENTITY_NAME,
                ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();
        List<Pet> pets = new ArrayList<>();
        for (JsonNode node : items) {
            Pet pet = mapJsonNodeNodeToPetWithTechnicalId(node);
            pets.add(pet);
        }
        return ResponseEntity.ok(new PetsResponse(pets));
    }

    @PostMapping("/add")
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid PetAddRequest addRequest) throws Exception {
        logger.info("Adding new pet: {}", addRequest);
        Pet newPet = new Pet(null, addRequest.getName(), addRequest.getType(), addRequest.getStatus(), addRequest.getPhotoUrls());
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                newPet,
                this::processPet);
        UUID technicalId = idFuture.join();
        // convert UUID to Long if possible, else null
        Long id = uuidToLong(technicalId);
        return ResponseEntity.ok(new AddPetResponse(id, "Pet added successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") @NotNull @Min(1) Long id) throws Exception {
        logger.info("Fetching pet by id {}", id);
        // fetch all items and find one with matching technicalId
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();
        for (JsonNode node : items) {
            UUID technicalId = UUID.fromString(node.path("technicalId").asText());
            if (id.equals(uuidToLong(technicalId))) {
                Pet pet = mapJsonNodeNodeToPetWithTechnicalId(node);
                return ResponseEntity.ok(pet);
            }
        }
        logger.warn("Pet not found with id {}", id);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
    }

    // helper to map JsonNode to Pet
    private Pet mapJsonNodeNodeToPetWithTechnicalId(JsonNode node) {
        UUID technicalId = UUID.fromString(node.path("technicalId").asText());
        Long id = uuidToLong(technicalId);
        String name = node.path("name").asText(null);
        String type = node.path("type").asText(null);
        String status = node.path("status").asText(null);
        List<String> photoUrls = new ArrayList<>();
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            for (JsonNode urlNode : node.get("photoUrls")) {
                photoUrls.add(urlNode.asText());
            }
        }
        return new Pet(id, name, type, status, photoUrls);
    }

    // original mapping from Petstore API JSON
    private Pet mapJsonNodeToPet(JsonNode node) {
        long id = node.path("id").asLong();
        String name = node.path("name").asText(null);
        String type = node.path("category").path("name").asText(null);
        String status = node.path("status").asText(null);
        List<String> photoUrls = new ArrayList<>();
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            for (JsonNode urlNode : node.get("photoUrls")) {
                photoUrls.add(urlNode.asText());
            }
        }
        return new Pet(id, name, type, status, photoUrls);
    }

    private boolean matchesQuery(Pet pet, PetQueryRequest query) {
        if (query.getType() != null && !query.getType().equalsIgnoreCase(pet.getType())) {
            return false;
        }
        if (query.getName() != null && (pet.getName() == null || !pet.getName().toLowerCase().contains(query.getName().toLowerCase()))) {
            return false;
        }
        return true;
    }

    // simple UUID to Long conversion by least significant bits, may lose uniqueness but matches expected Long type for id
    private Long uuidToLong(UUID uuid) {
        if (uuid == null) return null;
        long val = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return val < 0 ? -val : val;
    }

    // utility for parsing JSON string
    private static class JsonUtils {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static JsonNode parseJson(String json) throws Exception {
            return mapper.readTree(json);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetQueryRequest {
        @Size(max = 50)
        private String type;
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(min = 1, max = 100)
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetAddRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 50)
        private String type;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        @JsonIgnore
        private Long id; // original id not used by entityService, map from technicalId on retrieval
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetsResponse {
        private List<Pet> pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetResponse {
        private Long id;
        private String message;
    }
}
```
---

### Explanation of changes:
- Added a private workflow function `processPet(Pet pet)` in the controller, which matches the required naming convention `process{entity_name}`.
- Updated calls to `entityService.addItem(...)` to include the workflow function as the last parameter.
- For `queryPets`, since `addItems` batch method signature is not specified with workflow, switched to adding pets one-by-one with `addItem` and the workflow function.
- The `processPet` method currently just returns the pet as is, but you can add any pre-persistence logic inside it.
- Kept all other code as-is to preserve existing functionality.

Let me know if you want me to update other methods or batch addItems with workflow once you confirm the signature.