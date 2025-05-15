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
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

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

    private final Map<String, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping
    public ResponseEntity<AddUpdatePetResponse> addOrUpdatePet(@RequestBody @Valid PetRequest petRequest) {
        log.info("Received addOrUpdatePet request: {}", petRequest);
        Pet pet;
        if (petRequest.getId() != null && petStore.containsKey(petRequest.getId())) {
            pet = petStore.get(petRequest.getId());
            pet.setName(petRequest.getName());
            pet.setCategory(petRequest.getCategory());
            pet.setStatus(petRequest.getStatus());
            pet.setAge(petRequest.getAge());
            pet.setBreed(petRequest.getBreed());
            pet.setDescription(petRequest.getDescription());
            log.info("Updated pet with id {}", petRequest.getId());
        } else {
            String id = petRequest.getId() != null ? petRequest.getId() : UUID.randomUUID().toString();
            pet = new Pet(id, petRequest.getName(), petRequest.getCategory(),
                          petRequest.getStatus(), petRequest.getAge(),
                          petRequest.getBreed(), petRequest.getDescription());
            petStore.put(id, pet);
            log.info("Created new pet with id {}", id);
        }
        triggerPetAddedWorkflow(pet);
        return ResponseEntity.ok(new AddUpdatePetResponse(true, pet));
    }

    @PostMapping("/search")
    public ResponseEntity<SearchPetsResponse> searchPets(@RequestBody @Valid SearchPetsRequest searchRequest) {
        log.info("Received searchPets request: {}", searchRequest);
        try {
            List<Pet> results = new ArrayList<>();
            if (StringUtils.hasText(searchRequest.getStatus())) {
                String url = PETSTORE_API_BASE + "/findByStatus?status=" + searchRequest.getStatus();
                String jsonResponse = restTemplate.getForObject(url, String.class);
                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                if (rootNode.isArray()) {
                    for (JsonNode node : rootNode) {
                        Pet pet = mapJsonNodeToPet(node);
                        if (matchesSearch(pet, searchRequest)) results.add(pet);
                    }
                }
            } else {
                for (Pet pet : petStore.values()) {
                    if (matchesSearch(pet, searchRequest)) results.add(pet);
                }
            }
            return ResponseEntity.ok(new SearchPetsResponse(results));
        } catch (Exception ex) {
            log.error("Error during searchPets", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to search pets: " + ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) {
        log.info("Received getPetById request for id {}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet with id " + id + " not found");
        }
        return ResponseEntity.ok(pet);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getReason());
        log.error("Handled ResponseStatusException: {}", ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    private Pet mapJsonNodeToPet(JsonNode node) {
        String id = node.has("id") ? node.get("id").asText() : UUID.randomUUID().toString();
        String name = node.has("name") ? node.get("name").asText() : "";
        String category = node.has("category") && node.get("category").has("name")
                ? node.get("category").get("name").asText() : "";
        String status = node.has("status") ? node.get("status").asText() : "";
        return new Pet(id, name, category, status, null, null, null); // details omitted for prototype
    }

    private boolean matchesSearch(Pet pet, SearchPetsRequest req) {
        if (req.getCategory() != null && !req.getCategory().equalsIgnoreCase(pet.getCategory())) return false;
        if (req.getName() != null && !pet.getName().toLowerCase().contains(req.getName().toLowerCase()))
            return false;
        if (req.getStatus() != null && !req.getStatus().equalsIgnoreCase(pet.getStatus())) return false;
        return true;
    }

    @Async
    void triggerPetAddedWorkflow(Pet pet) {
        CompletableFuture.runAsync(() -> {
            log.info("Workflow triggered for pet id={} at {}", pet.getId(), Instant.now());
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            log.info("Workflow completed for pet id={}", pet.getId());
        });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetRequest {
        private String id;
        @NotBlank @Size(max = 100)
        private String name;
        @NotBlank @Size(max = 50)
        private String category;
        @NotBlank @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull private Integer age;
        @NotBlank @Size(max = 50) private String breed;
        @Size(max = 250) private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchPetsRequest {
        @Size(max = 50) private String category;
        @Size(max = 20) private String status;
        @Size(max = 100) private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private String id;
        private String name;
        private String category;
        private String status;
        private Integer age;
        private String breed;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddUpdatePetResponse {
        private boolean success;
        private Pet pet;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchPetsResponse {
        private List<Pet> results;
    }
}