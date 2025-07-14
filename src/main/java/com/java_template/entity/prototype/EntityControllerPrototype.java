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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage for local pets
    private final Map<String, Pet> localPets = new ConcurrentHashMap<>();

    // === Models ===
    @Data
    public static class Pet {
        private String id;
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        private Integer age;
        @NotBlank
        private String status; // "available" or "adopted"
    }

    @Data
    public static class SearchCriteria {
        private String type;
        private String status;
        private String name;
    }

    @Data
    public static class UpdatePetRequest {
        private String name;
        private String type;
        private Integer age;
        private String status;
    }

    @Data
    public static class MessageResponse {
        private String id;
        private String message;
    }

    // === Endpoint Implementations ===

    /**
     * GET /prototype/pets
     * List all pets from local storage
     */
    @GetMapping
    public Collection<Pet> listPets() {
        logger.info("Listing all local pets, count={}", localPets.size());
        return localPets.values();
    }

    /**
     * POST /prototype/pets/search
     * Search pets by criteria, combining local and external Petstore API data
     */
    @PostMapping("/search")
    public List<Pet> searchPets(@RequestBody SearchCriteria criteria) {
        logger.info("Search pets with criteria: type={}, status={}, name={}", criteria.getType(), criteria.getStatus(), criteria.getName());

        List<Pet> results = new ArrayList<>();

        // Search local pets
        localPets.values().stream()
                .filter(pet -> matchesCriteria(pet, criteria))
                .forEach(results::add);

        // Query Petstore API external data
        try {
            // Build Petstore API URL with query params if applicable - 
            // Petstore API does not support direct search, so we'll fetch all available pets by status if possible
            // For demo, only fetch by status = available or skip if no status given

            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=available"; // default to available
            if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
                url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + criteria.getStatus();
            }
            logger.info("Fetching external pets from Petstore API: {}", url);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    Pet extPet = new Pet();
                    extPet.setId(node.path("id").asText());
                    extPet.setName(node.path("name").asText());
                    JsonNode categoryNode = node.path("category");
                    if (!categoryNode.isMissingNode()) {
                        extPet.setType(categoryNode.path("name").asText("unknown"));
                    } else {
                        extPet.setType("unknown");
                    }
                    // Age not provided by Petstore API, set as null
                    extPet.setAge(null);
                    extPet.setStatus(criteria.getStatus() != null ? criteria.getStatus() : "available");

                    if (matchesCriteria(extPet, criteria)) {
                        results.add(extPet);
                    }
                }
            } else {
                logger.warn("Unexpected format from Petstore API: root is not an array");
            }
        } catch (Exception e) {
            logger.error("Error fetching data from Petstore API", e);
            // Continue with local results only on failure
        }

        return results;
    }

    private boolean matchesCriteria(Pet pet, SearchCriteria criteria) {
        if (criteria.getType() != null && !criteria.getType().isBlank()
                && !criteria.getType().equalsIgnoreCase(pet.getType())) {
            return false;
        }
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()
                && !criteria.getStatus().equalsIgnoreCase(pet.getStatus())) {
            return false;
        }
        if (criteria.getName() != null && !criteria.getName().isBlank()
                && !pet.getName().toLowerCase().contains(criteria.getName().toLowerCase())) {
            return false;
        }
        return true;
    }

    /**
     * POST /prototype/pets
     * Add new pet to local storage
     */
    @PostMapping
    public MessageResponse addPet(@Valid @RequestBody Pet pet) {
        String newId = UUID.randomUUID().toString();
        pet.setId(newId);
        localPets.put(newId, pet);
        logger.info("Added new pet with id={}", newId);
        MessageResponse resp = new MessageResponse();
        resp.setId(newId);
        resp.setMessage("Pet added successfully");
        return resp;
    }

    /**
     * POST /prototype/pets/{id}
     * Update pet info in local storage
     */
    @PostMapping("/{id}")
    public MessageResponse updatePet(@PathVariable String id, @RequestBody UpdatePetRequest update) {
        Pet existing = localPets.get(id);
        if (existing == null) {
            logger.error("Pet id={} not found for update", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (update.getName() != null) {
            existing.setName(update.getName());
        }
        if (update.getType() != null) {
            existing.setType(update.getType());
        }
        if (update.getAge() != null) {
            existing.setAge(update.getAge());
        }
        if (update.getStatus() != null) {
            existing.setStatus(update.getStatus());
        }
        logger.info("Updated pet id={}", id);
        MessageResponse resp = new MessageResponse();
        resp.setId(id);
        resp.setMessage("Pet updated successfully");
        return resp;
    }

    /**
     * POST /prototype/pets/{id}/adopt
     * Mark a pet as adopted
     */
    @PostMapping("/{id}/adopt")
    public MessageResponse adoptPet(@PathVariable String id) {
        Pet existing = localPets.get(id);
        if (existing == null) {
            logger.error("Pet id={} not found for adoption", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        existing.setStatus("adopted");
        logger.info("Pet id={} marked as adopted", id);
        MessageResponse resp = new MessageResponse();
        resp.setId(id);
        resp.setMessage("Pet adopted successfully");
        return resp;
    }

    // === Basic error handler for validation errors ===
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationExceptions(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        logger.error("Validation failed: {}", errors);
        return errors;
    }

}