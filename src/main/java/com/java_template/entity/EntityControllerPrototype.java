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

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @PostConstruct
    public void initMockData() {
        // Initialize with some mock pets
        petStore.put(1L, new Pet(1L, "Fluffy", "cat", "available",
                Arrays.asList("cute", "small"), Arrays.asList("http://example.com/photo1.jpg")));
        petStore.put(2L, new Pet(2L, "Barky", "dog", "pending",
                Collections.singletonList("friendly"), Collections.singletonList("http://example.com/photo2.jpg")));
    }

    @PostMapping
    public ResponseEntity<AddOrUpdateResponse> addOrUpdatePet(@RequestBody Pet pet) {
        if (pet.getId() == null) {
            pet.setId(generatePetId());
        }
        petStore.put(pet.getId(), pet);
        log.info("Added/Updated pet with id {}", pet.getId());

        // TODO: Fire-and-forget async sync with Petstore API or validation if needed
        CompletableFuture.runAsync(() -> {
            try {
                // Example: validate pet category by calling Petstore API categories (no direct endpoint exists - placeholder)
                // TODO: Implement real validation if required
                log.info("Async validation for pet id {} started", pet.getId());
                Thread.sleep(500); // simulate delay
                log.info("Async validation for pet id {} completed", pet.getId());
            } catch (InterruptedException e) {
                log.error("Async validation interrupted for pet id {}", pet.getId(), e);
                Thread.currentThread().interrupt();
            }
        });

        return ResponseEntity.ok(new AddOrUpdateResponse(true, pet.getId(), "Pet added/updated successfully"));
    }

    @PostMapping("/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody PetSearchRequest request) {
        log.info("Searching pets with filters: category='{}', status='{}', name='{}'",
                request.getCategory(), request.getStatus(), request.getName());

        List<Pet> result = new ArrayList<>();
        for (Pet pet : petStore.values()) {
            if ((request.getCategory() == null || pet.getCategory().equalsIgnoreCase(request.getCategory()))
                    && (request.getStatus() == null || pet.getStatus().equalsIgnoreCase(request.getStatus()))
                    && (request.getName() == null || pet.getName().toLowerCase().contains(request.getName().toLowerCase()))) {
                result.add(pet);
            }
        }
        log.info("Found {} pets matching search criteria", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable Long id) {
        log.info("Retrieving pet by id {}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            log.error("Pet with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<SimpleResponse> deletePet(@PathVariable Long id) {
        log.info("Deleting pet with id {}", id);
        Pet removed = petStore.remove(id);
        if (removed == null) {
            log.error("Pet with id {} not found for deletion", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(new SimpleResponse(true, "Pet deleted successfully"));
    }

    // Example of a method that fetches pet info from external Petstore API (not exposed as endpoint)
    private Optional<JsonNode> fetchPetFromExternalApi(Long petId) {
        try {
            String url = PETSTORE_API_BASE + "/pet/" + petId;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            return Optional.of(jsonNode);
        } catch (Exception e) {
            log.error("Error fetching pet from external API for id {}", petId, e);
            return Optional.empty();
        }
    }

    private long generatePetId() {
        return petStore.keySet().stream().max(Comparator.naturalOrder()).orElse(0L) + 1;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handling ResponseStatusException: {}", ex.getReason());
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getReason());
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    // --- DTOs and Model classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String category;
        private String status; // e.g. available, pending, sold
        private List<String> tags;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddOrUpdateResponse {
        private boolean success;
        private Long petId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetSearchRequest {
        private String category;
        private String status;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleResponse {
        private boolean success;
        private String message;
    }
}
```