```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // Simulated in-memory pet store (id -> Pet)
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private volatile long idSeq = 1L;

    // === DTOs ===

    @Data
    public static class PetRequest {
        @NotBlank
        private String action; // "fetch", "add", or "update"
        private Pet pet; // optional for fetch
    }

    @Data
    public static class Pet {
        private Long id;
        @NotBlank
        private String name;
        @NotBlank
        private String category;
        @NotBlank
        private String status; // "available", "pending", "sold"
    }

    @Data
    public static class SearchRequest {
        private Filters filters;
    }

    @Data
    public static class Filters {
        private String status; // optional "available", "pending", "sold"
        private String category; // optional
        private String nameContains; // optional
    }

    // === POST /pets ===
    // Handles fetch/add/update operations. 
    @PostMapping
    public ResponseEntity<?> postPets(@Valid @RequestBody PetRequest request) {
        String action = request.getAction().toLowerCase().trim();
        logger.info("Received POST /pets with action: {}", action);

        switch (action) {
            case "fetch":
                // For demo, fetch a random pet from external Petstore API and add it locally
                try {
                    // TODO: Real external Petstore API endpoint - here we use Swagger Petstore sample
                    String externalUrl = "https://petstore.swagger.io/v2/pet/1"; // fixed petId=1 for prototype
                    String response = restTemplate.getForObject(externalUrl, String.class);

                    JsonNode rootNode = objectMapper.readTree(response);
                    Pet pet = new Pet();
                    pet.setId(rootNode.path("id").asLong(idSeq++));
                    pet.setName(rootNode.path("name").asText("Unknown"));
                    pet.setCategory(rootNode.path("category").path("name").asText("Unknown"));
                    pet.setStatus(rootNode.path("status").asText("available"));

                    petStore.put(pet.getId(), pet);

                    logger.info("Fetched and stored pet from external API: {}", pet);
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "pet", pet,
                            "message", "Fetched pet from external Petstore API"
                    ));
                } catch (Exception e) {
                    logger.error("Error fetching pet from external API", e);
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pet from external API");
                }

            case "add":
                Pet addPet = request.getPet();
                if (addPet == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet data required for add action");
                }
                addPet.setId(idSeq++);
                petStore.put(addPet.getId(), addPet);
                logger.info("Added new pet: {}", addPet);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "pet", addPet,
                        "message", "Pet added successfully"
                ));

            case "update":
                Pet updatePet = request.getPet();
                if (updatePet == null || updatePet.getId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet data with id required for update action");
                }
                Pet existing = petStore.get(updatePet.getId());
                if (existing == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet with id " + updatePet.getId() + " not found");
                }
                // Update fields
                existing.setName(updatePet.getName());
                existing.setCategory(updatePet.getCategory());
                existing.setStatus(updatePet.getStatus());
                logger.info("Updated pet id {}: {}", updatePet.getId(), existing);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "pet", existing,
                        "message", "Pet updated successfully"
                ));

            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported action: " + action);
        }
    }

    // === POST /pets/search ===
    // Search pets by filters
    @PostMapping("/search")
    public ResponseEntity<?> searchPets(@RequestBody SearchRequest request) {
        logger.info("Received POST /pets/search with filters: {}", request.getFilters());

        List<Pet> filtered = petStore.values().stream()
                .filter(pet -> {
                    Filters f = request.getFilters();
                    if (f == null) return true;
                    if (f.getStatus() != null && !f.getStatus().equalsIgnoreCase(pet.getStatus())) return false;
                    if (f.getCategory() != null && !pet.getCategory().toLowerCase().contains(f.getCategory().toLowerCase()))
                        return false;
                    if (f.getNameContains() != null && !pet.getName().toLowerCase().contains(f.getNameContains().toLowerCase()))
                        return false;
                    return true;
                })
                .collect(Collectors.toList());

        logger.info("Search returned {} pets", filtered.size());
        return ResponseEntity.ok(Map.of("results", filtered));
    }

    // === GET /pets/{id} ===
    // Get pet details by id
    @GetMapping("/{id}")
    public ResponseEntity<?> getPetById(@PathVariable Long id) {
        logger.info("Received GET /pets/{}", id);

        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet with id " + id + " not found");
        }
        return ResponseEntity.ok(pet);
    }

    // === GET /pets ===
    // List all pets
    @GetMapping
    public ResponseEntity<?> listAllPets() {
        logger.info("Received GET /pets");

        List<Pet> allPets = petStore.values().stream().toList();
        return ResponseEntity.ok(allPets);
    }

    // === Basic Exception Handler for ResponseStatusException with minimal error info ===
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: status={}, message={}", ex.getStatusCode(), ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of(
                "error", ex.getStatusCode().toString(),
                "message", ex.getReason()
        ));
    }
}
```