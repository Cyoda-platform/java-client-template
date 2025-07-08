package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private long petIdSequence = 1;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String details;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SyncRequest {
        @NotBlank
        @Pattern(regexp = "(?i)sync", message = "action must be 'sync'")
        private String action;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetRequest {
        @NotBlank
        @Size(max = 50)
        private String name;

        @NotBlank
        @Size(max = 30)
        private String type;

        @NotBlank
        @Size(max = 20)
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class StatusUpdateRequest {
        @NotBlank
        @Size(max = 20)
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class GenericResponse {
        private String message;
        private Long id;
        private String status;
        private Integer syncedCount;
    }

    @PostMapping("/sync")
    public ResponseEntity<GenericResponse> syncPets(@RequestBody @Valid SyncRequest request) {
        logger.info("Received sync request with action={}", request.getAction());
        CompletableFuture.runAsync(this::performSyncFromPetstore);
        return ResponseEntity.ok(new GenericResponse("sync started", null, "processing", null));
    }

    @GetMapping
    public ResponseEntity<Object> getAllPets() {
        logger.info("Fetching all pets, count={}", petStore.size());
        return ResponseEntity.ok(petStore.values());
    }

    @PostMapping
    public ResponseEntity<GenericResponse> addPet(@RequestBody @Valid AddPetRequest request) {
        logger.info("Adding new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());
        long newId = getNextPetId();
        Pet newPet = new Pet(newId, request.getName(), request.getType(), request.getStatus(), null);
        petStore.put(newId, newPet);
        return ResponseEntity.status(HttpStatus.CREATED).body(new GenericResponse("Pet added successfully", newId, null, null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @Min(1) Long id) {
        logger.info("Fetching pet by id={}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/{id}/update-status")
    public ResponseEntity<GenericResponse> updatePetStatus(@PathVariable @Min(1) Long id,
                                                           @RequestBody @Valid StatusUpdateRequest request) {
        logger.info("Updating status for pet id={} to {}", id, request.getStatus());
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        pet.setStatus(request.getStatus());
        petStore.put(id, pet);
        return ResponseEntity.ok(new GenericResponse("Status updated successfully", id, null, null));
    }

    private synchronized long getNextPetId() {
        return petIdSequence++;
    }

    @Async
    private void performSyncFromPetstore() {
        logger.info("Starting petstore sync at {}", Instant.now());
        try {
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            String jsonResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            if (!root.isArray()) {
                logger.error("Unexpected response format");
                return;
            }
            petStore.clear();
            petIdSequence = 1;
            int count = 0;
            for (JsonNode node : root) {
                Long externalId = node.path("id").asLong(-1);
                String name = node.path("name").asText(null);
                String status = node.path("status").asText("Available");
                String type = node.path("category").path("name").asText("Unknown");
                if (externalId < 0 || name == null) {
                    continue;
                }
                long newId = getNextPetId();
                petStore.put(newId, new Pet(newId, name, type, status, null));
                count++;
            }
            logger.info("Petstore sync completed, imported {} pets", count);
        } catch (Exception e) {
            logger.error("Error during petstore sync", e);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: status={}, message={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", HttpStatus.INTERNAL_SERVER_ERROR.toString(), "message", "Internal server error"));
    }
}