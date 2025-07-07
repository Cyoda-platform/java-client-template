package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
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

import java.net.URI;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Simple in-memory cache for last fetched pets list
    private final Map<Long, Pet> cachedPets = new ConcurrentHashMap<>();

    // Simulate adoption status updates (petId -> adopted)
    private final Map<Long, Boolean> adoptionStatus = new ConcurrentHashMap<>();

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @PostMapping("/fetch")
    public ResponseEntity<PetsResponse> fetchPets(@RequestBody FetchRequest fetchRequest) {
        logger.info("Received fetch request with filters: type={}, status={}", fetchRequest.getType(), fetchRequest.getStatus());

        try {
            // Build external API URL with optional query parameters
            String uri = PETSTORE_API_BASE + "/pet/findByStatus?status=";
            String statusQuery = (fetchRequest.getStatus() == null || fetchRequest.getStatus().isBlank())
                    ? "available" : fetchRequest.getStatus().toLowerCase();

            // Petstore API supports multiple comma-separated status values but here we use one
            uri += statusQuery;

            logger.info("Calling external Petstore API: {}", uri);

            // Call external Petstore API
            String responseBody = restTemplate.getForObject(new URI(uri), String.class);

            JsonNode rootNode = objectMapper.readTree(responseBody);

            // Filter by type if specified
            List<Pet> pets = objectMapper.convertValue(rootNode, objectMapper.getTypeFactory().constructCollectionType(List.class, Pet.class))
                    .stream()
                    .filter(pet -> fetchRequest.getType() == null || fetchRequest.getType().isBlank() || pet.getType().equalsIgnoreCase(fetchRequest.getType()))
                    .collect(Collectors.toList());

            // Cache pets by id
            cachedPets.clear();
            pets.forEach(pet -> cachedPets.put(pet.getId(), pet));

            logger.info("Fetched {} pets from external API and cached", pets.size());

            return ResponseEntity.ok(new PetsResponse(pets));
        } catch (Exception ex) {
            logger.error("Error fetching pets from external API", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    @GetMapping
    public ResponseEntity<PetsResponse> getCachedPets() {
        logger.info("Returning cached pets, total count: {}", cachedPets.size());
        return ResponseEntity.ok(new PetsResponse(List.copyOf(cachedPets.values())));
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptResponse> adoptPet(@RequestBody @NotNull AdoptRequest adoptRequest) {
        logger.info("Received adoption request for petId={}", adoptRequest.getPetId());

        Pet pet = cachedPets.get(adoptRequest.getPetId());
        if (pet == null) {
            logger.error("Pet with id {} not found in cache", adoptRequest.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        // Update adoption status in-memory (simulate workflow trigger)
        adoptionStatus.put(adoptRequest.getPetId(), true);

        logger.info("Pet with id {} marked as adopted", adoptRequest.getPetId());

        // TODO: fire-and-forget workflow/job to persist adoption and notify systems asynchronously

        return ResponseEntity.ok(new AdoptResponse(true, "Pet adoption status updated."));
    }

    // --- DTOs ---

    @Data
    public static class FetchRequest {
        private String type;   // optional, e.g., "cat", "dog"
        private String status; // optional, e.g., "available", "sold", "pending"
    }

    @Data
    public static class AdoptRequest {
        @NotNull
        private Long petId;
    }

    @Data
    public static class PetsResponse {
        private List<Pet> pets;

        public PetsResponse(List<Pet> pets) {
            this.pets = pets;
        }
    }

    @Data
    public static class AdoptResponse {
        private boolean success;
        private String message;

        public AdoptResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String status;
        private String type;
        private List<String> photoUrls;
    }

    // --- Minimal error handling for ResponseStatusException ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
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
}