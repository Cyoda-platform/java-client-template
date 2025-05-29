```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final Map<Long, Pet> petsStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private long idSequence = 100; // Simulate auto-increment ID

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    // --- DTOs ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchPetsRequest {
        private String status; // available|pending|sold (optional)
        private String type;   // cat|dog|bird|... (optional)
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePetRequest {
        private String name;
        private String type;
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        private String status; // sold|pending|available
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
        private Integer count;
        private Long id;
        private String oldStatus;
        private String newStatus;
    }

    // --- API Endpoints ---

    /**
     * POST /pets/fetch
     * Fetch pet data from Petstore API based on filters, store in local map.
     */
    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse fetchPets(@RequestBody FetchPetsRequest request) {
        log.info("Received fetchPets request with filters status={} type={}", request.getStatus(), request.getType());

        // Build Petstore API URL to find pets by status (Petstore API supports status filter)
        // Petstore API endpoint: GET /pet/findByStatus?status=available
        // Since requirement mandates POST in our app for external calls, we do GET internally here.
        if (request.getStatus() == null || request.getStatus().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status filter must be provided for Petstore API query");
        }

        String url = PETSTORE_API_BASE + "/findByStatus?status=" + request.getStatus();

        JsonNode petstoreResponse;
        try {
            String responseStr = restTemplate.getForObject(url, String.class);
            petstoreResponse = objectMapper.readTree(responseStr);
        } catch (Exception e) {
            log.error("Error fetching data from Petstore API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch data from Petstore API");
        }

        int countStored = 0;
        if (petstoreResponse.isArray()) {
            for (JsonNode petNode : petstoreResponse) {
                // Filter by type if provided (petstore returns category.name as type)
                String petType = petNode.path("category").path("name").asText(null);
                if (request.getType() != null && !request.getType().isEmpty() &&
                    (petType == null || !petType.equalsIgnoreCase(request.getType()))) {
                    continue; // skip non-matching type
                }

                // Extract fields, using fallback/defaults where needed
                Long petId = petNode.path("id").asLong(++idSequence); // fallback to new id if missing
                String name = petNode.path("name").asText("Unnamed");
                String status = request.getStatus(); // from request filter
                Integer age = null; // Petstore API doesn't provide age, so leave null
                String description = petNode.path("description").asText(null);

                Pet pet = new Pet(petId, name, petType, status, age, description);
                petsStore.put(petId, pet);
                countStored++;
            }
        }

        log.info("Stored {} pets fetched from Petstore API", countStored);
        return new MessageResponse("Pets data fetched and stored successfully", countStored, null, null, null);
    }

    /**
     * POST /pets
     * Create a new pet record manually.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse createPet(@RequestBody CreatePetRequest request) {
        log.info("Creating new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());

        if (request.getName() == null || request.getName().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet name is required");
        }
        if (request.getType() == null || request.getType().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet type is required");
        }
        if (request.getStatus() == null || request.getStatus().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet status is required");
        }

        Long newId = ++idSequence;
        Pet pet = new Pet(newId, request.getName(), request.getType(), request.getStatus(), request.getAge(), request.getDescription());
        petsStore.put(newId, pet);

        log.info("Pet created with id={}", newId);
        return new MessageResponse("Pet created successfully", null, newId, null, null);
    }

    /**
     * POST /pets/{id}/status
     * Update pet status and trigger workflow.
     */
    @PostMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse updatePetStatus(@PathVariable("id") Long id, @RequestBody UpdateStatusRequest request) {
        log.info("Updating pet(id={}) status to {}", id, request.getStatus());

        Pet pet = petsStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (request.getStatus() == null || request.getStatus().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }

        String oldStatus = pet.getStatus();
        pet.setStatus(request.getStatus());
        petsStore.put(id, pet);

        // TODO: fire-and-forget workflow trigger for status change using Cyoda state machine
        CompletableFuture.runAsync(() -> {
            log.info("Triggered workflow for pet(id={}) status change from {} to {}", id, oldStatus, request.getStatus());
            // Implement actual workflow logic here
        });

        return new MessageResponse("Pet status updated", null, id, oldStatus, request.getStatus());
    }

    /**
     * GET /pets
     * Retrieve pets list, optionally filtered by status and type.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> getPets(@RequestParam(value = "status", required = false) String status,
                             @RequestParam(value = "type", required = false) String type) {
        log.info("Fetching pets list with filters status={} type={}", status, type);
        List<Pet> filtered = new ArrayList<>();

        for (Pet pet : petsStore.values()) {
            if (status != null && !status.isEmpty() && !status.equalsIgnoreCase(pet.getStatus())) {
                continue;
            }
            if (type != null && !type.isEmpty() && !type.equalsIgnoreCase(pet.getType())) {
                continue;
            }
            filtered.add(pet);
        }

        log.info("Returning {} pets after filtering", filtered.size());
        return filtered;
    }

    /**
     * GET /pets/{id}
     * Retrieve single pet details.
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") Long id) {
        log.info("Fetching pet details for id={}", id);
        Pet pet = petsStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    // --- Basic error handler ---

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason());
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("status", ex.getStatusCode().value());
        errorMap.put("error", ex.getReason());
        return errorMap;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("status", 500);
        errorMap.put("error", "Internal server error");
        return errorMap;
    }
}
```
