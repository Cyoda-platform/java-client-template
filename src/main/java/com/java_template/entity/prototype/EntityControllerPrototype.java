package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
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
@Validated
@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, Pet> pets = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> syncJobs = new ConcurrentHashMap<>();

    @Data @NoArgsConstructor @AllArgsConstructor
    static class Pet {
        private String id;
        private String name;
        private String type;
        private Integer age;
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class SyncRequest {
        @NotBlank
        private String source;
        private String type;
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class SyncResponse {
        private int syncedCount;
        private String message;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class AddPetRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotNull @Min(0)
        private Integer age;
        @NotBlank
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class UpdatePetRequest {
        @NotBlank
        private String id;
        private String name;
        private String type;
        @Min(0)
        private Integer age;
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class DeletePetRequest {
        @NotBlank
        private String id;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> syncPets(@RequestBody @Valid SyncRequest request) {
        logger.info("Received sync request from source={} type={} status={}",
            request.getSource(), request.getType(), request.getStatus());
        if (!"petstore".equalsIgnoreCase(request.getSource())) {
            throw new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Unsupported source: " + request.getSource()
            );
        }
        String jobId = UUID.randomUUID().toString();
        syncJobs.put(jobId, new JobStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> {
            try {
                int count = performPetstoreSync(request.getType(), request.getStatus());
                syncJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Sync job {} completed. {} pets synced.", jobId, count);
            } catch (Exception e) {
                syncJobs.put(jobId, new JobStatus("failed", Instant.now()));
                logger.error("Sync job {} failed: {}", jobId, e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(new SyncResponse(0, "Sync started, jobId=" + jobId));
    }

    private int performPetstoreSync(String typeFilter, String statusFilter) throws Exception {
        String statusParam = statusFilter != null ? statusFilter : "available";
        URI uri = new URI("https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam);
        logger.info("Fetching pets from Petstore API: {}", uri);
        String raw = restTemplate.getForObject(uri, String.class);
        if (raw == null) throw new IllegalStateException("Empty response from Petstore API");
        JsonNode root = objectMapper.readTree(raw);
        if (!root.isArray()) throw new IllegalStateException("Unexpected response format");
        int count = 0;
        for (JsonNode node : root) {
            String petType = node.path("category").path("name").asText(null);
            if (typeFilter != null && !typeFilter.equalsIgnoreCase(petType)) continue;
            String petId = node.path("id").asText(UUID.randomUUID().toString());
            String petName = node.path("name").asText("Unnamed");
            Integer petAge = null; // TODO: no age in Petstore API
            Pet pet = new Pet(petId, petName, petType, petAge, statusParam);
            pets.put(petId, pet);
            count++;
        }
        return count;
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getPets(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String status
    ) {
        logger.info("Fetching pets with filters type={} status={}", type, status);
        List<Pet> result = pets.values().stream()
            .filter(p -> type == null || (p.getType() != null && p.getType().equalsIgnoreCase(type)))
            .filter(p -> status == null || (p.getStatus() != null && p.getStatus().equalsIgnoreCase(status)))
            .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add")
    public ResponseEntity<Pet> addPet(@RequestBody @Valid AddPetRequest request) {
        logger.info("Adding new pet: {}", request);
        String id = UUID.randomUUID().toString();
        Pet pet = new Pet(id, request.getName(), request.getType(), request.getAge(), request.getStatus());
        pets.put(id, pet);
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> updatePet(@RequestBody @Valid UpdatePetRequest request) {
        logger.info("Updating pet: {}", request);
        Pet existing = pets.get(request.getId());
        if (existing == null) throw new ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Pet not found with id: " + request.getId()
        );
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getType() != null) existing.setType(request.getType());
        if (request.getAge() != null) existing.setAge(request.getAge());
        if (request.getStatus() != null) existing.setStatus(request.getStatus());
        pets.put(existing.getId(), existing);
        return ResponseEntity.ok(Map.of("message", "Pet updated successfully"));
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> deletePet(@RequestBody @Valid DeletePetRequest request) {
        logger.info("Deleting pet with id: {}", request.getId());
        Pet removed = pets.remove(request.getId());
        if (removed == null) throw new ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND,
            "Pet not found with id: " + request.getId()
        );
        return ResponseEntity.ok(Map.of("message", "Pet deleted successfully"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                         "message", "Internal server error"));
    }
}