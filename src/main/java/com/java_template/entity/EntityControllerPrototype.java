```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // To simulate last fetched pets result storage
    private volatile List<Pet> lastFetchedPets = Collections.emptyList();

    private long petIdSequence = 1L;

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    // ========== Data models ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetFetchRequest {
        private String type;
        private String status;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetAddRequest {
        private String name;
        private String type;
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetUpdateRequest {
        private String name;
        private String type;
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetAddResponse {
        private String message;
        private Long petId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    // ========== Endpoints ==========

    /**
     * POST /pets/fetch
     * Fetch pet data from external Petstore API based on filters.
     * External data retrieval + business logic here.
     */
    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<Pet>>> fetchPets(@RequestBody PetFetchRequest fetchRequest) {
        log.info("Received fetch request: {}", fetchRequest);

        try {
            // Build query parameters for Petstore API
            // Petstore API supports /pet/findByStatus?status=available,sold...
            // We'll prioritize status filter, then name/type filtering client-side

            List<Pet> resultPets = new ArrayList<>();

            // Prepare status param
            String statusParam = StringUtils.hasText(fetchRequest.getStatus()) ? fetchRequest.getStatus() : "available";

            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusParam;

            log.info("Calling external Petstore API: {}", url);
            String responseJson = restTemplate.getForObject(url, String.class);

            if (responseJson == null) {
                log.error("Empty response from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned empty response");
            }

            JsonNode rootNode = objectMapper.readTree(responseJson);

            if (!rootNode.isArray()) {
                log.error("Unexpected Petstore API response format, expected array");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned unexpected data");
            }

            // Map external pet JSON to internal Pet model, apply type and name filters client side
            for (JsonNode petNode : rootNode) {
                Long id = petNode.has("id") ? petNode.get("id").asLong() : null;
                String name = petNode.has("name") ? petNode.get("name").asText() : null;
                // Petstore API uses "category" for type: {id, name}
                String type = null;
                if (petNode.has("category") && petNode.get("category").has("name")) {
                    type = petNode.get("category").get("name").asText();
                }
                String status = petNode.has("status") ? petNode.get("status").asText() : null;

                // Age and description are not provided by Petstore API - add TODO or dummy values
                Integer age = null; // TODO: age not provided by external API
                String description = null; // TODO: description not provided by external API

                // Apply additional filters from request
                if (StringUtils.hasText(fetchRequest.getType()) && (type == null || !type.equalsIgnoreCase(fetchRequest.getType()))) {
                    continue;
                }
                if (StringUtils.hasText(fetchRequest.getName()) && (name == null || !name.toLowerCase().contains(fetchRequest.getName().toLowerCase()))) {
                    continue;
                }

                Pet pet = new Pet(id, name, type, status, age, description);
                resultPets.add(pet);
            }

            // Store last fetched pets in memory for retrieval via GET /pets
            this.lastFetchedPets = Collections.unmodifiableList(resultPets);

            Map<String, List<Pet>> response = Map.of("pets", resultPets);
            log.info("Returning {} pets after filtering", resultPets.size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching pets from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API: " + e.getMessage());
        }
    }

    /**
     * GET /pets
     * Retrieve list of last fetched or stored pets.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<Pet>>> getPets() {
        log.info("Returning last fetched pet list, count: {}", lastFetchedPets.size());
        return ResponseEntity.ok(Map.of("pets", lastFetchedPets));
    }

    /**
     * POST /pets/add
     * Add a new pet to the system (in-memory).
     */
    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetAddResponse> addPet(@RequestBody PetAddRequest addRequest) {
        log.info("Adding new pet: {}", addRequest);

        if (!StringUtils.hasText(addRequest.getName()) || !StringUtils.hasText(addRequest.getType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet name and type are required");
        }

        long newId = generatePetId();
        Pet newPet = new Pet(
                newId,
                addRequest.getName(),
                addRequest.getType(),
                addRequest.getStatus() != null ? addRequest.getStatus() : "available",
                addRequest.getAge(),
                addRequest.getDescription()
        );

        petStore.put(newId, newPet);

        log.info("Pet added with id {}", newId);
        return ResponseEntity.ok(new PetAddResponse("Pet added successfully", newId));
    }

    /**
     * GET /pets/{id}
     * Retrieve pet details by ID.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pet> getPetById(@PathVariable("id") Long id) {
        log.info("Fetching pet by id {}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + id);
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * POST /pets/update/{id}
     * Update pet details by ID.
     */
    @PostMapping(value = "/update/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> updatePet(@PathVariable("id") Long id, @RequestBody PetUpdateRequest updateRequest) {
        log.info("Updating pet id {} with data: {}", id, updateRequest);

        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + id);
        }

        // Update fields if present
        if (StringUtils.hasText(updateRequest.getName())) {
            pet.setName(updateRequest.getName());
        }
        if (StringUtils.hasText(updateRequest.getType())) {
            pet.setType(updateRequest.getType());
        }
        if (StringUtils.hasText(updateRequest.getStatus())) {
            pet.setStatus(updateRequest.getStatus());
        }
        if (updateRequest.getAge() != null) {
            pet.setAge(updateRequest.getAge());
        }
        if (updateRequest.getDescription() != null) {
            pet.setDescription(updateRequest.getDescription());
        }

        petStore.put(id, pet);

        log.info("Pet id {} updated", id);
        return ResponseEntity.ok(new MessageResponse("Pet updated successfully"));
    }

    // ========== Utility ==========

    private synchronized long generatePetId() {
        return petIdSequence++;
    }

    // ========== Basic error handling ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        Map<String, String> error = Map.of(
                "status", String.valueOf(ex.getStatusCode().value()),
                "error", ex.getReason()
        );
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, String> error = Map.of(
                "status", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                "error", "Internal server error"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```
