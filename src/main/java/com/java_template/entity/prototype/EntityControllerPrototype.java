```java
package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cache pet details fetched from external API after search (GET /pets/{id} reads from here)
    private final Map<Long, Pet> petCache = new ConcurrentHashMap<>();

    // Adoption requests stored in-memory as prototype persistence
    private final Map<String, AdoptionRequest> adoptionRequests = new ConcurrentHashMap<>();

    private static final String PETSTORE_BASE = "https://petstore.swagger.io/v2";

    // ========== DTOs ==========

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;
        private String name;
        private String type;    // Petstore uses "category" with "name"; simplified here
        private String status;
        private String[] photoUrls;
        private String[] tags;
    }

    @Data
    static class SearchRequest {
        private String type;
        private String status;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SearchResponse {
        private Pet[] pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AdoptionRequest {
        private String adoptionId;
        private Long petId;
        private String adopterName;
        private String adopterContact;
        private String status;      // pending, approved, rejected
        private String message;
        private Instant requestedAt;
    }

    @Data
    static class AdoptRequestBody {
        private Long petId;
        private String adopterName;
        private String adopterContact;
    }

    // ========== API Endpoints ==========

    /**
     * POST /prototype/pets/search
     * Search pets using filters.
     * Retrieves from external Petstore API, applies simple filtering and caches results.
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody SearchRequest request) {
        logger.info("Received search request: type='{}', status='{}', name='{}'",
                request.getType(), request.getStatus(), request.getName());

        try {
            // Petstore API: GET /pet/findByStatus?status=available,sold,pending
            // We'll query by status if provided, else default to "available"
            String statusParam = Optional.ofNullable(request.getStatus()).orElse("available");
            String url = PETSTORE_BASE + "/pet/findByStatus?status=" + statusParam;

            String responseStr = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(responseStr);

            // Filter results by type and name if provided
            var filteredPets = rootNode.findValuesAsText("id").stream()
                    .map(idStr -> {
                        try {
                            JsonNode petNode = rootNode.findValue(idStr);
                            return petNode;
                        } catch (Exception e) {
                            return null;
                        }
                    }).toList();

            // Because rootNode is an array, let's filter manually:
            var petsListNode = rootNode.isArray() ? rootNode : objectMapper.createArrayNode();

            var filteredPetsList = objectMapper.createArrayNode();

            for (JsonNode petNode : petsListNode) {
                boolean matches = true;
                if (request.getType() != null && !request.getType().isBlank()) {
                    // Petstore pet "category" may be null
                    JsonNode categoryNode = petNode.path("category");
                    String petType = categoryNode.path("name").asText("");
                    if (!petType.equalsIgnoreCase(request.getType())) {
                        matches = false;
                    }
                }
                if (request.getName() != null && !request.getName().isBlank()) {
                    String petName = petNode.path("name").asText("");
                    if (!petName.toLowerCase().contains(request.getName().toLowerCase())) {
                        matches = false;
                    }
                }
                if (matches) {
                    filteredPetsList.add(petNode);
                }
            }

            // Map filtered pets to Pet DTO and cache them
            Pet[] petsArray = new Pet[filteredPetsList.size()];
            int idx = 0;
            for (JsonNode petNode : filteredPetsList) {
                Pet pet = mapJsonNodeToPet(petNode);
                if (pet != null) {
                    petCache.put(pet.getId(), pet);
                    petsArray[idx++] = pet;
                }
            }

            logger.info("Search returned {} pets", idx);

            return ResponseEntity.ok(new SearchResponse(petsArray));

        } catch (Exception e) {
            logger.error("Error during pet search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error searching pets: " + e.getMessage());
        }
    }

    /**
     * GET /prototype/pets/{id}
     * Retrieve pet details from internal cache.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") Long id) {
        logger.info("Get pet by id: {}", id);
        Pet pet = petCache.get(id);
        if (pet == null) {
            logger.error("Pet with id {} not found in cache", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Pet not found with id " + id);
        }
        return ResponseEntity.ok(pet);
    }

    /**
     * POST /prototype/pets/adopt
     * Submit an adoption request.
     * Validates pet availability before accepting adoption.
     */
    @PostMapping("/adopt")
    public ResponseEntity<AdoptionRequest> adoptPet(@RequestBody AdoptRequestBody request) {
        logger.info("Adoption request received for petId={} by {}", request.getPetId(), request.getAdopterName());

        Pet pet = petCache.get(request.getPetId());
        if (pet == null) {
            logger.error("Pet with id {} not found in cache for adoption", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Pet not found with id " + request.getPetId());
        }

        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            logger.info("Pet id={} is not available for adoption. Status={}", pet.getId(), pet.getStatus());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AdoptionRequest(
                            null,
                            pet.getId(),
                            request.getAdopterName(),
                            request.getAdopterContact(),
                            "rejected",
                            "Pet is not available for adoption",
                            Instant.now()));
        }

        String adoptionId = UUID.randomUUID().toString();
        AdoptionRequest adoptionRequest = new AdoptionRequest(
                adoptionId,
                pet.getId(),
                request.getAdopterName(),
                request.getAdopterContact(),
                "pending",
                "Your adoption request is pending approval",
                Instant.now());

        adoptionRequests.put(adoptionId, adoptionRequest);

        // TODO: Fire-and-forget adoption processing (approve/reject) asynchronously
        CompletableFuture.runAsync(() -> processAdoption(adoptionId));

        logger.info("Adoption request {} stored and processing started", adoptionId);
        return ResponseEntity.ok(adoptionRequest);
    }

    /**
     * GET /prototype/pets/adoptions/{adoptionId}
     * Retrieve adoption request status.
     */
    @GetMapping("/adoptions/{adoptionId}")
    public ResponseEntity<AdoptionRequest> getAdoptionStatus(@PathVariable("adoptionId") String adoptionId) {
        logger.info("Retrieve adoption status for id: {}", adoptionId);
        AdoptionRequest adoption = adoptionRequests.get(adoptionId);
        if (adoption == null) {
            logger.error("Adoption request with id {} not found", adoptionId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Adoption request not found with id " + adoptionId);
        }
        return ResponseEntity.ok(adoption);
    }

    // ========== Helpers ==========

    private Pet mapJsonNodeToPet(JsonNode petNode) {
        try {
            Long id = petNode.path("id").asLong();
            String name = petNode.path("name").asText("");
            JsonNode categoryNode = petNode.path("category");
            String type = categoryNode.isMissingNode() ? "" : categoryNode.path("name").asText("");
            String status = petNode.path("status").asText("");
            JsonNode photosNode = petNode.path("photoUrls");
            String[] photoUrls = objectMapper.convertValue(photosNode, String[].class);
            JsonNode tagsNode = petNode.path("tags");
            String[] tags = objectMapper.convertValue(tagsNode.findValuesAsText("name"), String[].class);

            return new Pet(id, name, type, status, photoUrls, tags);
        } catch (Exception e) {
            logger.error("Error mapping pet JSON to Pet DTO", e);
            return null;
        }
    }

    private void processAdoption(String adoptionId) {
        logger.info("Processing adoption asynchronously for id: {}", adoptionId);

        // TODO: Replace with real adoption approval logic
        try {
            Thread.sleep(3000); // Simulate delay

            AdoptionRequest adoption = adoptionRequests.get(adoptionId);
            if (adoption == null) {
                logger.error("Adoption request not found during processing: {}", adoptionId);
                return;
            }

            // For prototype, just approve all pending requests
            adoption.setStatus("approved");
            adoption.setMessage("Your adoption request has been approved! Thank you.");

            // Update pet status in cache to "sold"
            Pet pet = petCache.get(adoption.getPetId());
            if (pet != null) {
                pet.setStatus("sold");
                petCache.put(pet.getId(), pet);
            }

            logger.info("Adoption request {} approved and pet status updated", adoptionId);

        } catch (InterruptedException e) {
            logger.error("Adoption processing interrupted for id: {}", adoptionId, e);
            Thread.currentThread().interrupt();
        }
    }

    // ========== Minimal Global Error Handler ==========

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "message", "Internal server error"
                ));
    }
}
```