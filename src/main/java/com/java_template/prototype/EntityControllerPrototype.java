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
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Mock storage for fetched pets, keyed by pet ID
    private final Map<Integer, Pet> petStorage = new ConcurrentHashMap<>();

    // External Petstore API base URL
    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    /**
     * POST /pets/fetch
     * Fetch and process pet data from external Petstore API with optional filters
     */
    @PostMapping("/fetch")
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody(required = false) FetchPetsRequest request) {
        String filterStatus = null;
        if (request != null && request.getFilter() != null) {
            filterStatus = request.getFilter().getStatus();
        }
        if (filterStatus == null || filterStatus.isBlank()) {
            filterStatus = "available"; // default status
        }

        logger.info("Fetching pets from external API with status filter: {}", filterStatus);

        try {
            // Call external Petstore API by status
            String url = PETSTORE_API_URL;
            JsonNode responseJson = restTemplate.getForObject(url, JsonNode.class, filterStatus);

            if (responseJson == null || !responseJson.isArray()) {
                logger.error("Unexpected response from Petstore API: not an array");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }

            petStorage.clear(); // clear current cache

            for (JsonNode petNode : responseJson) {
                Pet pet = new Pet();

                pet.setId(petNode.path("id").asInt());
                pet.setName(petNode.path("name").asText(""));
                pet.setType(petNode.path("category").path("name").asText("unknown"));
                pet.setStatus(petNode.path("status").asText("unknown"));

                // Optional description from tags or empty
                if (petNode.has("tags") && petNode.get("tags").isArray() && petNode.get("tags").size() > 0) {
                    pet.setDescription(petNode.get("tags").get(0).path("name").asText("No description"));
                } else {
                    pet.setDescription("No description");
                }

                petStorage.put(pet.getId(), pet);
            }

            logger.info("Fetched and stored {} pets", petStorage.size());

            FetchPetsResponse fetchPetsResponse = new FetchPetsResponse();
            fetchPetsResponse.setPets(petStorage.values().stream().toList());

            return ResponseEntity.ok(fetchPetsResponse);

        } catch (Exception ex) {
            logger.error("Error fetching pets from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch data from external Petstore API");
        }
    }

    /**
     * GET /pets
     * Retrieve last fetched and processed pet data stored in the app
     */
    @GetMapping
    public ResponseEntity<GetPetsResponse> getPets() {
        logger.info("Returning {} stored pets", petStorage.size());
        GetPetsResponse response = new GetPetsResponse();
        response.setPets(petStorage.values().stream().toList());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /pets/adopt
     * Adopt a pet by updating its status to "adopted"
     */
    @PostMapping("/adopt")
    public ResponseEntity<AdoptPetResponse> adoptPet(@RequestBody @Validated AdoptPetRequest request) {
        logger.info("Adoption request received for petId={}", request.getPetId());

        Pet pet = petStorage.get(request.getPetId());
        if (pet == null) {
            logger.error("Pet with id {} not found", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        if ("adopted".equalsIgnoreCase(pet.getStatus())) {
            logger.info("Pet id={} is already adopted", pet.getId());
            return ResponseEntity.ok(new AdoptPetResponse("Pet is already adopted", pet));
        }

        // Update status to adopted (mock update)
        pet.setStatus("adopted");
        petStorage.put(pet.getId(), pet);

        logger.info("Pet id={} adopted successfully", pet.getId());

        AdoptPetResponse response = new AdoptPetResponse("Pet adopted successfully", pet);
        return ResponseEntity.ok(response);
    }

    // Minimal error handler for ResponseStatusException
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // --- DTO classes ---

    @Data
    public static class FetchPetsRequest {
        private Filter filter;

        @Data
        public static class Filter {
            private String type;
            private String status;
        }
    }

    @Data
    public static class FetchPetsResponse {
        private java.util.List<Pet> pets;
    }

    @Data
    public static class GetPetsResponse {
        private java.util.List<Pet> pets;
    }

    @Data
    public static class AdoptPetRequest {
        @NotBlank(message = "petId must not be blank")
        private Integer petId;
    }

    @Data
    public static class AdoptPetResponse {
        private String message;
        private Pet pet;

        public AdoptPetResponse(String message, Pet pet) {
            this.message = message;
            this.pet = pet;
        }
    }

    @Data
    public static class Pet {
        private Integer id;
        private String name;
        private String type;
        private String status;
        private String description;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}