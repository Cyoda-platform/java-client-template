package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final Map<String, Pet> petStorage = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, JobStatus> importJobs = new ConcurrentHashMap<>();
    private static final String EXTERNAL_PETSTORE_API = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";

    @PostConstruct
    public void initSampleData() {
        Pet sample1 = new Pet(UUID.randomUUID().toString(), "Whiskers", "Cat", 3, "available", "Playful tabby cat");
        Pet sample2 = new Pet(UUID.randomUUID().toString(), "Barkley", "Dog", 5, "adopted", "Loyal golden retriever");
        petStorage.put(sample1.getId(), sample1);
        petStorage.put(sample2.getId(), sample2);
        log.info("Sample pets initialized");
    }

    @PostMapping("/import") // must be first
    public ResponseEntity<ImportResponse> importPets(@RequestBody @Valid ImportRequest request) {
        log.info("Received import request: {}", request);
        String jobId = UUID.randomUUID().toString();
        importJobs.put(jobId, new JobStatus("processing", Instant.now()));

        CompletableFuture.runAsync(() -> {
            try {
                String statusFilter = (request.getStatus() != null) ? request.getStatus() : "available";
                String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusFilter;
                log.info("Fetching from external API: {}", url);
                String json = restTemplate.getForObject(url, String.class);
                JsonNode array = objectMapper.readTree(json);
                int count = 0;
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        Pet pet = mapJsonNodeToPet(node);
                        petStorage.put(pet.getId(), pet);
                        count++;
                    }
                }
                importJobs.put(jobId, new JobStatus("completed", Instant.now()));
                log.info("Imported {} pets", count);
            } catch (Exception e) {
                importJobs.put(jobId, new JobStatus("failed", Instant.now()));
                log.error("Import failed", e);
            }
        });
        return ResponseEntity.ok(new ImportResponse(jobId, "Import started"));
    }

    @GetMapping // must be first
    public ResponseEntity<List<PetSummary>> getPets(
            @RequestParam(required = false) @Size(min = 1) String type,
            @RequestParam(required = false) @Size(min = 1) String status) {
        log.info("Listing pets type={} status={}", type, status);
        List<PetSummary> list = new ArrayList<>();
        petStorage.values().stream()
                .filter(p -> (type == null || p.getType().equalsIgnoreCase(type)) &&
                             (status == null || p.getStatus().equalsIgnoreCase(status)))
                .forEach(p -> list.add(new PetSummary(p.getId(), p.getName(), p.getType(), p.getAge(), p.getStatus())));
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}") // must be first
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) {
        log.info("Getting pet id={}", id);
        Pet pet = petStorage.get(id);
        if (pet == null) {
            log.error("Pet not found id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping // must be first
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid AddPetRequest request) {
        log.info("Adding pet: {}", request);
        String id = UUID.randomUUID().toString();
        Pet pet = new Pet(id, request.getName(), request.getType(), request.getAge(), request.getStatus(), request.getDescription());
        petStorage.put(id, pet);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AddPetResponse(id, "Pet added successfully"));
    }

    @PostMapping("/{id}/update-status") // must be first
    public ResponseEntity<UpdateStatusResponse> updatePetStatus(
            @PathVariable @NotBlank String id,
            @RequestBody @Valid UpdateStatusRequest request) {
        log.info("Updating status id={} to {}", id, request.getStatus());
        Pet pet = petStorage.get(id);
        if (pet == null) {
            log.error("Pet not found id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        pet.setStatus(request.getStatus());
        return ResponseEntity.ok(new UpdateStatusResponse(id, request.getStatus(), "Status updated successfully"));
    }

    private Pet mapJsonNodeToPet(JsonNode node) {
        try {
            String id = node.has("id") ? String.valueOf(node.get("id").asLong()) : UUID.randomUUID().toString();
            String name = node.has("name") ? node.get("name").asText() : "Unknown";
            String type = node.has("category") && node.get("category").has("name")
                    ? node.get("category").get("name").asText() : "unknown";
            String status = node.has("status") ? node.get("status").asText() : "unknown";
            return new Pet(id, name, type, 0, status, null);
        } catch (Exception e) {
            log.error("Mapping error", e);
            return new Pet(UUID.randomUUID().toString(), "Unknown", "unknown", 0, "unknown", null);
        }
    }

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
        @NotBlank
        private String source;
        @Size(min = 1)
        private String type;
        @Size(min = 1)
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImportResponse {
        private String jobId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @Min(0)
        private int age;
        @NotBlank
        private String status;
        @Size(max = 255)
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
        @NotBlank
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", String.valueOf(ex.getStatusCode().value()));
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unhandled exception caught: ", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("details", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}