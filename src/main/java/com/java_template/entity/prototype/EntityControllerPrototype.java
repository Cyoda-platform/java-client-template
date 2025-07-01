```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory pet store to simulate persistence
    private final Map<String, Pet> pets = new ConcurrentHashMap<>();

    // Job status store for sync jobs (fire-and-forget)
    private final Map<String, JobStatus> syncJobs = new ConcurrentHashMap<>();

    // --- Models ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private String id;
        private String name;
        private String type;
        private Integer age;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SyncRequest {
        private String source;
        private Filters filters;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        static class Filters {
            private String type;
            private String status;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SyncResponse {
        private int syncedCount;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetRequest {
        private String name;
        private String type;
        private Integer age;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UpdatePetRequest {
        private String id;
        private String name;
        private String type;
        private Integer age;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class DeletePetRequest {
        private String id;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    // --- API Endpoints ---

    /**
     * POST /pets/sync
     * Sync pet data from external Petstore API and update local store.
     * Business logic and external API fetch done here.
     */
    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> syncPets(@RequestBody SyncRequest request) {
        logger.info("Received sync request from source={} with filters={}", request.getSource(), request.getFilters());

        if (!"petstore".equalsIgnoreCase(request.getSource())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source: " + request.getSource());
        }

        // Create a jobId for tracking (not exposed here, but could be extended)
        String jobId = UUID.randomUUID().toString();
        syncJobs.put(jobId, new JobStatus("processing", Instant.now()));

        // Fire-and-forget sync processing
        CompletableFuture.runAsync(() -> {
            try {
                int count = performPetstoreSync(request.getFilters());
                syncJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Sync job {} completed. {} pets synced.", jobId, count);
            } catch (Exception e) {
                syncJobs.put(jobId, new JobStatus("failed", Instant.now()));
                logger.error("Sync job {} failed: {}", jobId, e.getMessage(), e);
            }
        });
        // Return immediate response - sync runs asynchronously
        return ResponseEntity.ok(new SyncResponse(0, "Sync started, jobId=" + jobId));
    }

    // Actual method to fetch and update pets from Petstore API
    private int performPetstoreSync(SyncRequest.Filters filters) throws Exception {
        String baseUrl = "https://petstore.swagger.io/v2/pet/findByStatus";

        // Petstore expects status param as comma separated values, here we map our filter.status simply
        String statusParam = (filters != null && filters.getStatus() != null) ? filters.getStatus() : "available";

        URI uri = new URI(baseUrl + "?status=" + statusParam);

        logger.info("Fetching pets from Petstore API: {}", uri);

        String rawResponse = restTemplate.getForObject(uri, String.class);
        if (rawResponse == null) {
            throw new IllegalStateException("Empty response from Petstore API");
        }

        JsonNode rootNode = objectMapper.readTree(rawResponse);

        if (!rootNode.isArray()) {
            throw new IllegalStateException("Unexpected Petstore API response format");
        }

        int syncedCount = 0;

        for (JsonNode petNode : rootNode) {
            String petType = petNode.path("category").path("name").asText(null);
            if (filters != null && filters.getType() != null && !filters.getType().equalsIgnoreCase(petType)) {
                continue; // Skip pets not matching filter.type
            }

            String petId = petNode.path("id").asText(UUID.randomUUID().toString());
            String petName = petNode.path("name").asText("Unnamed");
            // Age and status are not present in Petstore API, setting placeholders
            Integer petAge = null; // TODO: No age info in Petstore API; consider removing or mocking
            String petStatus = statusParam;

            Pet pet = new Pet(petId, petName, petType, petAge, petStatus);
            pets.put(petId, pet);
            syncedCount++;
        }
        return syncedCount;
    }

    /**
     * GET /pets
     * Retrieve stored pets with optional filtering by type and status.
     */
    @GetMapping
    public ResponseEntity<List<Pet>> getPets(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "status", required = false) String status) {

        logger.info("Fetching pets with filters type={} status={}", type, status);

        List<Pet> result = pets.values().stream()
                .filter(p -> (type == null || (p.getType() != null && p.getType().equalsIgnoreCase(type))))
                .filter(p -> (status == null || (p.getStatus() != null && p.getStatus().equalsIgnoreCase(status))))
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * POST /pets/add
     * Add a new pet to local store.
     */
    @PostMapping("/add")
    public ResponseEntity<Pet> addPet(@RequestBody AddPetRequest request) {
        logger.info("Adding new pet: {}", request);

        if (request.getName() == null || request.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name and Type are required");
        }

        String id = UUID.randomUUID().toString();
        Pet pet = new Pet(id, request.getName(), request.getType(), request.getAge(), request.getStatus());
        pets.put(id, pet);

        return ResponseEntity.ok(pet);
    }

    /**
     * POST /pets/update
     * Update pet details.
     */
    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> updatePet(@RequestBody UpdatePetRequest request) {
        logger.info("Updating pet: {}", request);

        if (request.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet id is required");
        }

        Pet existingPet = pets.get(request.getId());
        if (existingPet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id: " + request.getId());
        }

        if (request.getName() != null) existingPet.setName(request.getName());
        if (request.getType() != null) existingPet.setType(request.getType());
        if (request.getAge() != null) existingPet.setAge(request.getAge());
        if (request.getStatus() != null) existingPet.setStatus(request.getStatus());

        pets.put(existingPet.getId(), existingPet);

        return ResponseEntity.ok(Map.of("message", "Pet updated successfully"));
    }

    /**
     * POST /pets/delete
     * Delete pet by id.
     */
    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> deletePet(@RequestBody DeletePetRequest request) {
        logger.info("Deleting pet with id: {}", request.getId());

        if (request.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet id is required");
        }

        Pet removed = pets.remove(request.getId());
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id: " + request.getId());
        }

        return ResponseEntity.ok(Map.of("message", "Pet deleted successfully"));
    }

    // --- Basic error handling ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "message", "Internal server error"
                ));
    }
}
```