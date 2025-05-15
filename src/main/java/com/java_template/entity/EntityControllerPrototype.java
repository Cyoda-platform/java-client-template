```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final Map<String, Pet> petStorage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // Job status storage to simulate async import process
    private final Map<String, JobStatus> importJobs = new ConcurrentHashMap<>();

    // Sample external Petstore API URL for fetching pets - TODO replace if needed
    private static final String EXTERNAL_PETSTORE_API = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";

    @PostConstruct
    public void initSampleData() {
        // Add some sample pets for GET endpoints demonstration
        Pet sample1 = new Pet(UUID.randomUUID().toString(), "Whiskers", "Cat", 3, "available", "Playful tabby cat");
        Pet sample2 = new Pet(UUID.randomUUID().toString(), "Barkley", "Dog", 5, "adopted", "Loyal golden retriever");
        petStorage.put(sample1.getId(), sample1);
        petStorage.put(sample2.getId(), sample2);
        log.info("Sample pets initialized");
    }

    /**
     * POST /pets/import
     * Import or refresh pet data from external Petstore API.
     * Accepts optional filters, but currently only 'status' filter is used to call external API.
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResponse> importPets(@RequestBody ImportRequest request) {
        log.info("Received import request with filter: {}", request.getFilter());
        String jobId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        importJobs.put(jobId, new JobStatus("processing", requestedAt));

        CompletableFuture.runAsync(() -> {
            try {
                String statusFilter = (request.getFilter() != null && request.getFilter().getStatus() != null)
                        ? request.getFilter().getStatus()
                        : "available";
                String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusFilter;
                log.info("Fetching pet data from external API: {}", url);
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode petsArray = objectMapper.readTree(jsonResponse);

                int importedCount = 0;
                if (petsArray.isArray()) {
                    for (JsonNode petNode : petsArray) {
                        Pet pet = mapJsonNodeToPet(petNode);
                        petStorage.put(pet.getId(), pet);
                        importedCount++;
                    }
                }
                importJobs.put(jobId, new JobStatus("completed", Instant.now()));
                log.info("Imported {} pets from external API", importedCount);
            } catch (Exception e) {
                importJobs.put(jobId, new JobStatus("failed", Instant.now()));
                log.error("Failed to import pets", e);
            }
        }); // TODO: Consider proper async error handling and job status updates

        return ResponseEntity.ok(new ImportResponse(0, "Import job started with ID: " + jobId));
    }

    /**
     * GET /pets
     * Retrieve a list of pets, optional filtering by type and status.
     */
    @GetMapping
    public ResponseEntity<List<PetSummary>> getPets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        log.info("Fetching pets with filters - type: {}, status: {}", type, status);
        List<PetSummary> result = new ArrayList<>();
        petStorage.values().stream()
                .filter(pet -> (type == null || pet.getType().equalsIgnoreCase(type)) &&
                        (status == null || pet.getStatus().equalsIgnoreCase(status)))
                .forEach(pet -> result.add(new PetSummary(pet.getId(), pet.getName(), pet.getType(), pet.getAge(), pet.getStatus())));

        return ResponseEntity.ok(result);
    }

    /**
     * GET /pets/{id}
     * Retrieve details of a specific pet by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable String id) {
        log.info("Fetching pet details for id: {}", id);
        Pet pet = petStorage.get(id);
        if (pet == null) {
            log.error("Pet not found: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * POST /pets
     * Add a new pet.
     */
    @PostMapping
    public ResponseEntity<AddPetResponse> addPet(@RequestBody AddPetRequest request) {
        log.info("Adding new pet: {}", request);
        // Simple validation
        if (request.getName() == null || request.getType() == null || request.getStatus() == null) {
            log.error("Missing required pet fields");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required fields: name, type, status");
        }
        String id = UUID.randomUUID().toString();
        Pet pet = new Pet(id, request.getName(), request.getType(), request.getAge(), request.getStatus(), request.getDescription());
        petStorage.put(id, pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AddPetResponse(id, "Pet added successfully"));
    }

    /**
     * POST /pets/{id}/update-status
     * Update pet adoption status.
     */
    @PostMapping("/{id}/update-status")
    public ResponseEntity<UpdateStatusResponse> updatePetStatus(@PathVariable String id, @RequestBody UpdateStatusRequest request) {
        log.info("Updating status for pet id: {} to {}", id, request.getStatus());
        Pet pet = petStorage.get(id);
        if (pet == null) {
            log.error("Pet not found for status update: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            log.error("Invalid status update request");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be provided");
        }
        pet.setStatus(request.getStatus());
        petStorage.put(id, pet);
        return ResponseEntity.ok(new UpdateStatusResponse(id, request.getStatus(), "Status updated successfully"));
    }

    // Helper to map external JSON to Pet entity - simplified prototype mapping
    private Pet mapJsonNodeToPet(JsonNode petNode) {
        try {
            String id = petNode.has("id") ? String.valueOf(petNode.get("id").asLong()) : UUID.randomUUID().toString();
            String name = petNode.has("name") ? petNode.get("name").asText() : "Unknown";
            String type = "unknown";
            // Try to extract category.name as type if present
            if (petNode.has("category") && petNode.get("category").has("name")) {
                type = petNode.get("category").get("name").asText();
            }
            int age = 0; // external API doesn't provide age - default 0
            String status = petNode.has("status") ? petNode.get("status").asText() : "unknown";
            String description = petNode.has("photoUrls") ? "Photos available" : null;
            return new Pet(id, name, type, age, status, description);
        } catch (Exception e) {
            log.error("Error mapping external pet JSON to Pet entity", e);
            return new Pet(UUID.randomUUID().toString(), "Unknown", "unknown", 0, "unknown", null);
        }
    }

    // --- DTOs and Entities ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private String id;
        private String name;
        private String type;
        private int age;
        private String status;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class PetSummary {
        private String id;
        private String name;
        private String type;
        private int age;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImportRequest {
        private String source; // Expected to be "petstore"
        private Filter filter;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        static class Filter {
            private String type;   // optional
            private String status; // optional
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImportResponse {
        private int importedCount;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetRequest {
        private String name;
        private String type;
        private int age;
        private String status;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetResponse {
        private String id;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UpdateStatusRequest {
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UpdateStatusResponse {
        private String id;
        private String newStatus;
        private String message;
    }

    @Data
    @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant timestamp;
    }

    // --- Basic exception handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", String.valueOf(ex.getStatusCode().value()));
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception caught: ", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("details", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```
