```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // In-memory data store to mock persistence
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // External Petstore API base - using Swagger Petstore public URL
    private static final String EXTERNAL_PETSTORE_BASE = "https://petstore.swagger.io/v2/pet";

    // ==== Models ====

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String[] photoUrls;
    }

    @Data
    @NoArgsConstructor
    public static class SearchRequest {
        private String type;
        private String status;
    }

    @Data
    @NoArgsConstructor
    public static class IdRequest {
        private Long id;
    }

    // ==== Endpoints ====

    /**
     * POST /pets/search
     * Searches pets by type/status by invoking external Petstore API.
     * Returns filtered list based on external data.
     */
    @PostMapping("/search")
    public ResponseEntity<JsonNode> searchPets(@RequestBody SearchRequest request) {
        logger.info("Received searchPets request with type='{}', status='{}'", request.getType(), request.getStatus());

        try {
            // Call external API to get all pets by status (Petstore API supports status query param)
            String url = EXTERNAL_PETSTORE_BASE + "/findByStatus?status=" +
                    (StringUtils.hasText(request.getStatus()) ? request.getStatus() : "available");

            String rawResponse = restTemplate.getForObject(new URI(url), String.class);

            JsonNode petsNode = objectMapper.readTree(rawResponse);

            // Filter by type if provided
            if (StringUtils.hasText(request.getType())) {
                var filtered = petsNode.findValuesAsText("type").stream().anyMatch(type -> type.equalsIgnoreCase(request.getType()));
                // We need to filter the array nodes by type (manual filtering)
                var filteredArray = petsNode.isArray() ?
                        objectMapper.createArrayNode() : null;

                if (petsNode.isArray()) {
                    var arrNode = objectMapper.createArrayNode();
                    petsNode.forEach(petNode -> {
                        JsonNode petTypeNode = petNode.get("category");
                        String petType = petTypeNode != null && petTypeNode.has("name") ? petTypeNode.get("name").asText() : null;
                        if (request.getType().equalsIgnoreCase(petType)) {
                            arrNode.add(petNode);
                        }
                    });
                    return ResponseEntity.ok(arrNode);
                }
            }

            return ResponseEntity.ok(petsNode);
        } catch (Exception ex) {
            logger.error("Error while searching pets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error fetching pets: " + ex.getMessage());
        }
    }

    /**
     * POST /pets/add
     * Adds a new pet locally and optionally triggers external API add.
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addPet(@RequestBody Pet newPet) {
        logger.info("Adding new pet: {}", newPet);

        if (!StringUtils.hasText(newPet.getName()) || !StringUtils.hasText(newPet.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet name and type are required");
        }

        // Generate new ID (mock auto-increment)
        long newId = petStore.keySet().stream().mapToLong(Long::longValue).max().orElse(0L) + 1;
        newPet.setId(newId);

        petStore.put(newId, newPet);

        // TODO: Fire and forget external API call to add pet if needed
        CompletableFuture.runAsync(() -> {
            try {
                // Map our Pet to Petstore API format
                /*
                {
                  "id": 0,
                  "category": {
                    "id": 0,
                    "name": "string"
                  },
                  "name": "doggie",
                  "photoUrls": [
                    "string"
                  ],
                  "tags": [
                    {
                      "id": 0,
                      "name": "string"
                    }
                  ],
                  "status": "available"
                }
                */

                ObjectNode petstorePet = objectMapper.createObjectNode();
                petstorePet.put("id", newId);
                ObjectNode categoryNode = petstorePet.putObject("category");
                categoryNode.put("id", 0);
                categoryNode.put("name", newPet.getType());
                petstorePet.put("name", newPet.getName());

                var photoUrlsNode = petstorePet.putArray("photoUrls");
                if (newPet.getPhotoUrls() != null) {
                    for (String url : newPet.getPhotoUrls()) {
                        photoUrlsNode.add(url);
                    }
                }
                petstorePet.put("status", newPet.getStatus() != null ? newPet.getStatus() : "available");

                restTemplate.postForEntity(EXTERNAL_PETSTORE_BASE, petstorePet.toString(), String.class);
                logger.info("External Petstore API addPet fired for pet id {}", newId);
            } catch (Exception e) {
                logger.error("Failed to add pet to external Petstore API", e);
            }
        });

        return ResponseEntity.ok(Map.of("id", newId, "message", "Pet added successfully"));
    }

    /**
     * POST /pets/update
     * Updates pet information locally and triggers workflow for external sync.
     */
    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> updatePet(@RequestBody Pet updateRequest) {
        logger.info("Updating pet: {}", updateRequest);

        if (updateRequest.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet ID is required for update");
        }

        Pet existing = petStore.get(updateRequest.getId());
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + updateRequest.getId());
        }

        // Update fields if provided
        if (StringUtils.hasText(updateRequest.getName())) existing.setName(updateRequest.getName());
        if (StringUtils.hasText(updateRequest.getType())) existing.setType(updateRequest.getType());
        if (StringUtils.hasText(updateRequest.getStatus())) existing.setStatus(updateRequest.getStatus());
        if (updateRequest.getPhotoUrls() != null) existing.setPhotoUrls(updateRequest.getPhotoUrls());

        // Save back (mock)
        petStore.put(existing.getId(), existing);

        // Fire-and-forget workflow to sync with external Petstore API
        syncUpdateWithExternalAPI(existing);

        return ResponseEntity.ok(Map.of("message", "Pet updated successfully"));
    }

    @Async
    void syncUpdateWithExternalAPI(Pet pet) {
        try {
            ObjectNode petstorePet = objectMapper.createObjectNode();
            petstorePet.put("id", pet.getId());
            ObjectNode categoryNode = petstorePet.putObject("category");
            categoryNode.put("id", 0);
            categoryNode.put("name", pet.getType());
            petstorePet.put("name", pet.getName());

            var photoUrlsNode = petstorePet.putArray("photoUrls");
            if (pet.getPhotoUrls() != null) {
                for (String url : pet.getPhotoUrls()) {
                    photoUrlsNode.add(url);
                }
            }
            petstorePet.put("status", pet.getStatus() != null ? pet.getStatus() : "available");

            restTemplate.put(EXTERNAL_PETSTORE_BASE, petstorePet.toString());
            logger.info("External Petstore API updatePet synchronized for pet id {}", pet.getId());
        } catch (Exception e) {
            logger.error("Failed to sync pet update with external Petstore API for pet id {}", pet.getId(), e);
        }
    }

    /**
     * POST /pets/delete
     * Deletes a pet locally by ID.
     */
    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> deletePet(@RequestBody IdRequest idRequest) {
        logger.info("Deleting pet with id {}", idRequest.getId());

        if (idRequest.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet ID is required for deletion");
        }

        Pet removed = petStore.remove(idRequest.getId());
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + idRequest.getId());
        }

        // TODO: Optionally trigger external API delete asynchronously if supported
        // Currently, Petstore API does support DELETE /pet/{petId} but we skip it in prototype

        return ResponseEntity.ok(Map.of("message", "Pet deleted successfully"));
    }

    /**
     * GET /pets/{id}
     * Retrieves pet info by ID from local store.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable Long id) {
        logger.info("Retrieving pet by id {}", id);

        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + id);
        }

        return ResponseEntity.ok(pet);
    }

    // === Minimal error handling ===

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", HttpStatus.INTERNAL_SERVER_ERROR.toString(), "message", ex.getMessage()));
    }
}
```