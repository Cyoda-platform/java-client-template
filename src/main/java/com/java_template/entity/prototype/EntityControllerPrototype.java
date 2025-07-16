```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_PETSTORE_API = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory pet storage: id -> Pet
    private final Map<String, Pet> petStore = new ConcurrentHashMap<>();

    // --- DTOs ---

    @Data
    public static class Pet {
        private String id;
        @NotBlank
        private String name;
        @NotBlank
        private String type; // e.g. "cat", "dog"
        @NotBlank
        private String status; // e.g. "available", "sold"
        private String description;
        private String funFact; // optional fun fact
    }

    @Data
    public static class FetchPetsRequest {
        private String filterType;       // optional, e.g. "cat"
        private Boolean includeFunFacts; // optional
    }

    @Data
    public static class FetchPetsResponse {
        private int processedCount;
        private String message;
    }

    @Data
    public static class AddPetRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotBlank
        private String status;
        private String description;
    }

    @Data
    public static class AddPetResponse {
        private String id;
        private String message;
    }

    @Data
    public static class UpdateStatusRequest {
        @NotBlank
        private String status;
    }

    @Data
    public static class UpdateStatusResponse {
        private String id;
        private String message;
    }

    // --- Endpoints ---

    /**
     * GET /prototype/pets
     * Retrieve stored pets, optionally filtered by type and/or status.
     */
    @GetMapping
    public List<Pet> getPets(@RequestParam(required = false) String type,
                             @RequestParam(required = false) String status) {
        logger.info("GET /prototype/pets called with type={} and status={}", type, status);

        return petStore.values().stream()
                .filter(pet -> type == null || pet.getType().equalsIgnoreCase(type))
                .filter(pet -> status == null || pet.getStatus().equalsIgnoreCase(status))
                .toList();
    }

    /**
     * POST /prototype/pets/fetch
     * Fetch pets from external Petstore API, process and store them.
     */
    @PostMapping("/fetch")
    public ResponseEntity<FetchPetsResponse> fetchAndProcessPets(@RequestBody FetchPetsRequest request) {
        logger.info("POST /prototype/pets/fetch called with filterType={} and includeFunFacts={}",
                request.getFilterType(), request.getIncludeFunFacts());

        try {
            // Build external API URL with optional filterType
            String url = EXTERNAL_PETSTORE_API;
            if (request.getFilterType() != null && !request.getFilterType().isBlank()) {
                // The Petstore API does not support filtering by type directly,
                // so we will filter after fetching.
                logger.info("Note: external Petstore API doesn't support type filtering, will filter locally");
            }

            // Fetch pets from external Petstore API
            String jsonResponse = restTemplate.getForObject(URI.create(url), String.class);
            JsonNode rootNode = objectMapper.readTree(jsonResponse);

            int processedCount = 0;

            for (JsonNode petNode : rootNode) {
                // Petstore API pet object example fields: id, name, category{name}, status (not always present)
                String petName = petNode.path("name").asText(null);
                String petId = petNode.path("id").asText(null);
                String petType = petNode.path("category").path("name").asText(null);
                if (petType == null || petType.isBlank()) {
                    petType = "unknown";
                }
                String petStatus = "available"; // default since API endpoint only returns available

                if (petName == null || petId == null) {
                    logger.warn("Skipping pet with missing id or name");
                    continue;
                }

                // Filter by type if requested
                if (request.getFilterType() != null && !request.getFilterType().isBlank()) {
                    if (!petType.equalsIgnoreCase(request.getFilterType())) {
                        continue;
                    }
                }

                Pet pet = new Pet();
                pet.setId(UUID.randomUUID().toString());
                pet.setName(petName);
                pet.setType(petType.toLowerCase());
                pet.setStatus(petStatus);
                pet.setDescription("Imported from external Petstore API");

                if (Boolean.TRUE.equals(request.getIncludeFunFacts())) {
                    // Add simple fun fact (mocked)
                    pet.setFunFact("This " + pet.getType() + " loves sunny spots!"); // TODO: Replace with real fun facts source
                }

                petStore.put(pet.getId(), pet);
                processedCount++;
            }

            FetchPetsResponse response = new FetchPetsResponse();
            response.setProcessedCount(processedCount);
            response.setMessage("Pets fetched and processed successfully");

            logger.info("Processed {} pets from external API", processedCount);
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            logger.error("Failed to fetch/process pets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch/process pets");
        }
    }

    /**
     * POST /prototype/pets
     * Add a new pet.
     */
    @PostMapping
    public ResponseEntity<AddPetResponse> addPet(@RequestBody AddPetRequest request) {
        logger.info("POST /prototype/pets called to add pet: name={}, type={}, status={}",
                request.getName(), request.getType(), request.getStatus());

        try {
            Pet pet = new Pet();
            pet.setId(UUID.randomUUID().toString());
            pet.setName(request.getName());
            pet.setType(request.getType().toLowerCase());
            pet.setStatus(request.getStatus().toLowerCase());
            pet.setDescription(request.getDescription());

            petStore.put(pet.getId(), pet);

            AddPetResponse response = new AddPetResponse();
            response.setId(pet.getId());
            response.setMessage("Pet added successfully");

            logger.info("Pet added with id={}", pet.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception ex) {
            logger.error("Failed to add pet", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to add pet");
        }
    }

    /**
     * POST /prototype/pets/{id}/status
     * Update status of existing pet.
     */
    @PostMapping("/{id}/status")
    public ResponseEntity<UpdateStatusResponse> updatePetStatus(@PathVariable("id") String id,
                                                                @RequestBody UpdateStatusRequest request) {
        logger.info("POST /prototype/pets/{}/status called to update status to {}", id, request.getStatus());

        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet with id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        try {
            pet.setStatus(request.getStatus().toLowerCase());
            // petStore.put(id, pet); // Not needed, map value updated

            UpdateStatusResponse response = new UpdateStatusResponse();
            response.setId(id);
            response.setMessage("Pet status updated successfully");

            logger.info("Pet id={} status updated to {}", id, request.getStatus());
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            logger.error("Failed to update pet status for id={}", id, ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update pet status");
        }
    }

    // --- Minimal error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason() != null ? ex.getReason() : "Unexpected error"
                ));
    }
}
```