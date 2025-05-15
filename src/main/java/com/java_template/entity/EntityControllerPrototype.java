package com.java_template.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @PostConstruct
    public void initMockData() {
        petStore.put(1L, new Pet(1L, "Fluffy", "cat", "available",
                Arrays.asList("cute", "small"), Arrays.asList("http://example.com/photo1.jpg")));
        petStore.put(2L, new Pet(2L, "Barky", "dog", "pending",
                Collections.singletonList("friendly"), Collections.singletonList("http://example.com/photo2.jpg")));
    }

    @PostMapping
    public ResponseEntity<AddOrUpdateResponse> addOrUpdatePet(@RequestBody @Valid Pet pet) {
        if (pet.getId() == null) {
            pet.setId(generatePetId());
        }
        petStore.put(pet.getId(), pet);
        log.info("Added/Updated pet with id {}", pet.getId());
        CompletableFuture.runAsync(() -> {
            // TODO: async sync/validation with external API
            log.info("Async job started for pet id {}", pet.getId());
        });
        return ResponseEntity.ok(new AddOrUpdateResponse(true, pet.getId(), "Pet added/updated successfully"));
    }

    @PostMapping("/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid PetSearchRequest request) {
        log.info("Searching pets: category='{}', status='{}', name='{}'",
                request.getCategory(), request.getStatus(), request.getName());
        List<Pet> result = new ArrayList<>();
        for (Pet pet : petStore.values()) {
            if ((request.getCategory() == null || pet.getCategory().equalsIgnoreCase(request.getCategory()))
                    && (request.getStatus() == null || pet.getStatus().equalsIgnoreCase(request.getStatus()))
                    && (request.getName() == null || pet.getName().toLowerCase().contains(request.getName().toLowerCase()))) {
                result.add(pet);
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotNull Long id) {
        log.info("Retrieving pet by id {}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<SimpleResponse> deletePet(@PathVariable @NotNull Long id) {
        log.info("Deleting pet with id {}", id);
        Pet removed = petStore.remove(id);
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(new SimpleResponse(true, "Pet deleted successfully"));
    }

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
        Map<String, Object> body = new HashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("error", ex.getReason());
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        @NotBlank
        private String name;
        @NotBlank
        private String category;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> tags;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> photoUrls;
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
        @Size(min = 1)
        private String category;
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(min = 1)
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