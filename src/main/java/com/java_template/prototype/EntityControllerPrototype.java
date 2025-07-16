package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for pets keyed by petId
    private final Map<String, Pet> petsStore = new ConcurrentHashMap<>();

    // External Petstore API base URL (public Swagger Petstore)
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    // --- DTOs ---

    @Data
    public static class PetRequest {
        private String petId; // optional for new pets
        @NotBlank(message = "name must not be blank")
        private String name;
        @NotBlank(message = "category must not be blank")
        private String category;
        @NotBlank(message = "status must not be blank")
        private String status; // available|pending|sold
        private String description; // fun/playful description
    }

    @Data
    public static class PetDeleteRequest {
        @NotBlank(message = "petId must not be blank")
        private String petId;
    }

    @Data
    public static class PetSearchRequest {
        private String name;
        private String category;
        private String status;
    }

    @Data
    public static class PetResponse {
        private boolean success;
        private Pet pet;
    }

    @Data
    public static class DeleteResponse {
        private boolean success;
        private String message;
    }

    @Data
    public static class Pet {
        private String petId;
        private String name;
        private String category;
        private String status;
        private String description;
    }

    // --- Endpoints ---

    /**
     * Add or update a pet.
     * Business logic: if petId is provided, update existing pet or fetch from external API if missing.
     * If no petId, create new pet with generated UUID.
     * External API interaction: fetch pet info from Petstore API by petId (if updating).
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PetResponse addOrUpdatePet(@RequestBody PetRequest request) {
        logger.info("Received addOrUpdatePet request: {}", request);
        String petId = request.getPetId();

        Pet pet;
        if (petId != null && !petId.isBlank()) {
            // Update existing or fetch external pet data if not present
            pet = petsStore.get(petId);
            if (pet == null) {
                // Fetch from external Petstore API
                try {
                    String url = PETSTORE_API_BASE + "/" + petId;
                    String response = restTemplate.getForObject(url, String.class);
                    JsonNode externalPetJson = objectMapper.readTree(response);

                    pet = new Pet();
                    pet.setPetId(String.valueOf(externalPetJson.path("id").asText(petId)));
                    pet.setName(externalPetJson.path("name").asText(request.getName()));
                    pet.setCategory(externalPetJson.path("category").path("name").asText(request.getCategory()));
                    pet.setStatus(request.getStatus()); // override with request status
                    pet.setDescription(request.getDescription() != null ? request.getDescription() : "A lovely pet!");

                    logger.info("Fetched pet from external API and created local pet: {}", pet);

                } catch (Exception ex) {
                    logger.error("Error fetching pet from external API for petId {}: {}", petId, ex.getMessage());
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pet data from external API");
                }
            } else {
                // Update fields from request
                pet.setName(request.getName());
                pet.setCategory(request.getCategory());
                pet.setStatus(request.getStatus());
                pet.setDescription(request.getDescription());
                logger.info("Updated existing pet in store: {}", pet);
            }
        } else {
            // Create new pet with generated UUID
            pet = new Pet();
            pet.setPetId(UUID.randomUUID().toString());
            pet.setName(request.getName());
            pet.setCategory(request.getCategory());
            pet.setStatus(request.getStatus());
            pet.setDescription(request.getDescription() != null ? request.getDescription() : "A lovely pet!");
            logger.info("Created new pet: {}", pet);
        }

        petsStore.put(pet.getPetId(), pet);

        PetResponse response = new PetResponse();
        response.setSuccess(true);
        response.setPet(pet);
        return response;
    }

    /**
     * Retrieve all pets stored in the application.
     */
    @GetMapping
    public Collection<Pet> getPets() {
        logger.info("Retrieving all pets, count={}", petsStore.size());
        return petsStore.values();
    }

    /**
     * Search pets by optional filters (name partial match, category, status).
     * Business logic and filtering done here.
     */
    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Collection<Pet> searchPets(@RequestBody PetSearchRequest request) {
        logger.info("Searching pets with criteria: {}", request);

        List<Pet> results = new ArrayList<>();
        for (Pet pet : petsStore.values()) {
            if (matchesFilter(pet, request)) {
                results.add(pet);
            }
        }
        logger.info("Search returned {} results", results.size());
        return results;
    }

    private boolean matchesFilter(Pet pet, PetSearchRequest filter) {
        if (filter.getName() != null && !filter.getName().isBlank()) {
            if (!pet.getName().toLowerCase().contains(filter.getName().toLowerCase())) {
                return false;
            }
        }
        if (filter.getCategory() != null && !filter.getCategory().isBlank()) {
            if (!pet.getCategory().equalsIgnoreCase(filter.getCategory())) {
                return false;
            }
        }
        if (filter.getStatus() != null && !filter.getStatus().isBlank()) {
            if (!pet.getStatus().equalsIgnoreCase(filter.getStatus())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Delete pet by petId.
     */
    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeleteResponse deletePet(@RequestBody PetDeleteRequest request) {
        logger.info("Deleting pet with petId: {}", request.getPetId());
        Pet removed = petsStore.remove(request.getPetId());
        DeleteResponse response = new DeleteResponse();
        if (removed != null) {
            response.setSuccess(true);
            response.setMessage("Pet deleted successfully");
            logger.info("Pet deleted: {}", request.getPetId());
        } else {
            response.setSuccess(false);
            response.setMessage("Pet not found");
            logger.warn("Pet to delete not found: {}", request.getPetId());
        }
        return response;
    }

    // --- Basic Error Handling ---

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return errorBody;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        errorBody.put("message", "Internal server error");
        return errorBody;
    }
}