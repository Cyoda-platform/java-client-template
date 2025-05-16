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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final Map<Long, Pet> pets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private long currentId = 1L;

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;

        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String category;

        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;

        @NotNull
        @Size(max = 10)
        private List<@NotBlank String> tags = new ArrayList<>();

        @NotNull
        @Size(max = 10)
        private List<@NotBlank String> photoUrls = new ArrayList<>();
    }

    @Data
    static class SyncRequest {
        @Size(max = 200)
        private String sourceUrl;
    }

    @Data
    @AllArgsConstructor
    static class SyncResponse {
        private String status;
        private String message;
        private int count;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> syncPets(@RequestBody @Valid SyncRequest request) {
        String sourceUrl = request.getSourceUrl();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            sourceUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available,pending,sold";
        }
        log.info("Starting pet data sync from source: {}", sourceUrl);
        try {
            String rawJson = restTemplate.getForObject(sourceUrl, String.class);
            JsonNode rootNode = objectMapper.readTree(rawJson);
            if (!rootNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected JSON array from source");
            }
            int count = 0;
            for (JsonNode petNode : rootNode) {
                Pet pet = parsePetFromJsonNode(petNode);
                if (pet.getId() == null) {
                    pet.setId(generateId());
                }
                pets.put(pet.getId(), pet);
                count++;
            }
            log.info("Synchronized {} pets from external API", count);
            return ResponseEntity.ok(new SyncResponse("success", "Pets data synchronized", count));
        } catch (Exception e) {
            log.error("Failed to sync pets data", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to sync pets data: " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Pet> createPet(@RequestBody @Valid Pet pet) {
        pet.setId(generateId());
        pets.put(pet.getId(), pet);
        log.info("Created new pet with ID {}", pet.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @PostMapping("/{petId}")
    public ResponseEntity<Pet> updatePet(@PathVariable Long petId, @RequestBody @Valid Pet petUpdate) {
        Pet existing = pets.get(petId);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with ID " + petId);
        }
        existing.setName(petUpdate.getName());
        existing.setCategory(petUpdate.getCategory());
        existing.setStatus(petUpdate.getStatus());
        existing.setTags(petUpdate.getTags());
        existing.setPhotoUrls(petUpdate.getPhotoUrls());
        pets.put(petId, existing);
        log.info("Updated pet with ID {}", petId);
        return ResponseEntity.ok(existing);
    }

    @GetMapping("/{petId}")
    public ResponseEntity<Pet> getPetById(@PathVariable Long petId) {
        Pet pet = pets.get(petId);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with ID " + petId);
        }
        return ResponseEntity.ok(pet);
    }

    @GetMapping
    public ResponseEntity<List<Pet>> searchPets(
            @RequestParam(required = false) @Pattern(regexp = "available|pending|sold") String status,
            @RequestParam(required = false) @Size(max = 50) String category) {
        List<Pet> results = new ArrayList<>();
        for (Pet pet : pets.values()) {
            boolean matches = true;
            if (status != null && !status.equalsIgnoreCase(pet.getStatus())) {
                matches = false;
            }
            if (category != null && !category.equalsIgnoreCase(pet.getCategory())) {
                matches = false;
            }
            if (matches) {
                results.add(pet);
            }
        }
        log.info("Search pets with status='{}', category='{}' found {} results", status, category, results.size());
        return ResponseEntity.ok(results);
    }

    private Pet parsePetFromJsonNode(JsonNode petNode) {
        Pet pet = new Pet();
        pet.setId(petNode.path("id").isNumber() ? petNode.get("id").asLong() : null);
        pet.setName(petNode.path("name").asText(null));
        JsonNode categoryNode = petNode.path("category");
        if (!categoryNode.isMissingNode() && categoryNode.has("name")) {
            pet.setCategory(categoryNode.get("name").asText(null));
        }
        pet.setStatus(petNode.path("status").asText(null));
        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = petNode.path("tags");
        if (tagsNode.isArray()) {
            for (JsonNode tagNode : tagsNode) {
                if (tagNode.has("name")) {
                    tags.add(tagNode.get("name").asText());
                }
            }
        }
        pet.setTags(tags);
        List<String> photoUrls = new ArrayList<>();
        JsonNode photosNode = petNode.path("photoUrls");
        if (photosNode.isArray()) {
            for (JsonNode photoNode : photosNode) {
                photoUrls.add(photoNode.asText());
            }
        }
        pet.setPhotoUrls(photoUrls);
        return pet;
    }

    private synchronized long generateId() {
        return currentId++;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled exception: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", ex.getStatusCode().value());
        errorBody.put("error", ex.getStatusCode().getReasonPhrase());
        errorBody.put("message", ex.getReason());
        return new ResponseEntity<>(errorBody, ex.getStatusCode());
    }

    // TODO: If needed, add async methods to handle background sync jobs or caching
}