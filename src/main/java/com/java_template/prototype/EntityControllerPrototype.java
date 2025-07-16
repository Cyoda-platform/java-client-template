```java
package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String EXTERNAL_PETSTORE_BASE = "https://petstore.swagger.io/v2/pet";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory pet store: petId -> Pet
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();

    // --- POST /pets ---
    @PostMapping
    public ResponseEntity<PetResponse> addOrUpdatePet(@Valid @RequestBody AddOrUpdatePetRequest request) {
        logger.info("Received addOrUpdatePet request with source: {}", request.getSource());

        if ("external".equalsIgnoreCase(request.getSource())) {
            if (request.getPetId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId must be provided when source is 'external'");
            }
            try {
                String url = EXTERNAL_PETSTORE_BASE + "/" + request.getPetId();
                logger.info("Fetching pet data from external Petstore API: {}", url);
                String json = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(json);

                // Parsing relevant fields with safe fallback
                long id = root.path("id").asLong(-1);
                if (id == -1) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found in external API");
                }
                String name = root.path("name").asText(null);
                String status = root.path("status").asText(null);
                String type = "unknown";
                // Pet type is not directly defined in Petstore spec; TODO: map from category or tags if needed
                JsonNode categoryNode = root.path("category");
                if (!categoryNode.isMissingNode()) {
                    type = categoryNode.path("name").asText("unknown");
                }
                int age = 0; // external API doesn't provide age; TODO: decide how to handle age from external data

                Pet pet = new Pet(id, name, type, age, status);
                petStore.put(id, pet);
                logger.info("Stored external pet data with id {}", id);
                return ResponseEntity.ok(new PetResponse(true, pet));
            } catch (Exception ex) {
                logger.error("Error fetching external pet data", ex);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch external pet data");
            }
        } else if ("internal".equalsIgnoreCase(request.getSource())) {
            PetData data = request.getPetData();
            if (data == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petData must be provided when source is 'internal'");
            }
            long newId = request.getPetId() != null ? request.getPetId() : IdGenerator.nextId();
            Pet pet = new Pet(newId, data.getName(), data.getType(), data.getAge(), data.getStatus());
            petStore.put(newId, pet);
            logger.info("Stored internal pet data with id {}", newId);
            return ResponseEntity.ok(new PetResponse(true, pet));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source value. Must be 'external' or 'internal'");
        }
    }

    // --- POST /pets/search ---
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody SearchRequest request) {
        logger.info("Search pets with filters: type={}, status={}, minAge={}, maxAge={}",
                request.getType(), request.getStatus(), request.getMinAge(), request.getMaxAge());

        // TODO: Optionally fetch external data for search - currently only searching internal store

        List<Pet> results = petStore.values().stream()
                .filter(p -> request.getType() == null || request.getType().equalsIgnoreCase(p.getType()))
                .filter(p -> request.getStatus() == null || request.getStatus().equalsIgnoreCase(p.getStatus()))
                .filter(p -> request.getMinAge() == null || p.getAge() >= request.getMinAge())
                .filter(p -> request.getMaxAge() == null || p.getAge() <= request.getMaxAge())
                .toList();

        logger.info("Found {} matching pets", results.size());
        return ResponseEntity.ok(new SearchResponse(results));
    }

    // --- GET /pets/{id} ---
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") long id) {
        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.info("Pet with id {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        logger.info("Returning pet with id {}", id);
        return ResponseEntity.ok(pet);
    }

    // --- GET /pets ---
    @GetMapping
    public ResponseEntity<List<Pet>> getAllPets() {
        logger.info("Returning all pets, count={}", petStore.size());
        return ResponseEntity.ok(petStore.values().stream().toList());
    }

    // --- Exception handler ---
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    // --- Data classes ---

    @Data
    public static class AddOrUpdatePetRequest {
        @NotBlank
        private String source; // "external" or "internal"
        private Long petId; // optional for internal, required for external
        private PetData petData; // required if source is internal
    }

    @Data
    public static class PetData {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @Min(0)
        private int age;
        @NotBlank
        private String status;
    }

    @Data
    public static class PetResponse {
        private boolean success;
        private Pet pet;

        public PetResponse(boolean success, Pet pet) {
            this.success = success;
            this.pet = pet;
        }
    }

    @Data
    public static class SearchRequest {
        private String type;
        private String status;
        private Integer minAge;
        private Integer maxAge;
    }

    @Data
    public static class SearchResponse {
        private List<Pet> results;

        public SearchResponse(List<Pet> results) {
            this.results = results;
        }
    }

    @Data
    public static class ErrorResponse {
        private String error;
        private String message;

        public ErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }
    }

    @Data
    public static class Pet {
        private long id;
        private String name;
        private String type;
        private int age;
        private String status;

        public Pet(long id, String name, String type, int age, String status) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.age = age;
            this.status = status;
        }
    }

    // Simple Id generator for internal pets
    static class IdGenerator {
        private static long currentId = 1000;

        static synchronized long nextId() {
            return currentId++;
        }
    }
}
```