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
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory storage for pets list and pet details
    private final ConcurrentMap<Integer, PetSummary> petStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, PetDetails> petDetailsStore = new ConcurrentHashMap<>();

    // Job tracking for async fetch requests
    private final ConcurrentMap<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String EXTERNAL_API_BASE = "https://petstore.swagger.io/v2";

    // Simple executor for async tasks
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /*
     * ========== POST /pets/fetch ==========
     * Request: filter by type and status (optional)
     * Fetches pet list from external API and stores internally after processing
     */
    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody FetchPetsRequest request) {
        log.info("Received fetch request with filter: type={}, status={}", request.getFilter().getType(), request.getFilter().getStatus());

        // Fire-and-forget async processing
        CompletableFuture.runAsync(() -> {
            try {
                // Build query params
                String url = EXTERNAL_API_BASE + "/pet/findByStatus?status=" +
                        (request.getFilter().getStatus() != null ? request.getFilter().getStatus() : "available,pending,sold");
                // TODO: Petstore doesn't support filtering by type, so filtering by type will be done locally

                log.info("Calling external Petstore API: {}", url);
                String response = restTemplate.getForObject(url, String.class);
                JsonNode rootNode = objectMapper.readTree(response);

                int countProcessed = 0;
                petStore.clear();

                if (rootNode.isArray()) {
                    for (JsonNode petNode : rootNode) {
                        PetSummary pet = parsePetSummary(petNode);
                        if (request.getFilter().getType() == null || request.getFilter().getType().equalsIgnoreCase(pet.getType())) {
                            petStore.put(pet.getId(), pet);
                            countProcessed++;
                        }
                    }
                } else {
                    log.warn("Unexpected response format from external API: expected array.");
                }
                log.info("Processed {} pets after filtering", countProcessed);

            } catch (Exception e) {
                log.error("Failed to fetch or process pets from external API", e);
                // In production, consider retry or alerting
            }
        }, executor);

        // Immediate response - in real app, might return job id for status tracking
        return ResponseEntity.ok(new FetchPetsResponse("Pets data fetch started", petStore.size()));
    }

    /*
     * ========== GET /pets ==========
     * Returns stored pet list (processed from last fetch)
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PetSummary>> getPets() {
        log.info("Returning stored list of pets, count={}", petStore.size());
        return ResponseEntity.ok(List.copyOf(petStore.values()));
    }

    /*
     * ========== POST /pets/details ==========
     * Request: petId - fetch detailed info about one pet from external API, store locally
     */
    @PostMapping(value = "/details", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FetchPetDetailsResponse> fetchPetDetails(@RequestBody FetchPetDetailsRequest request) {
        int petId = request.getPetId();
        log.info("Received request to fetch details for petId={}", petId);

        try {
            String url = EXTERNAL_API_BASE + "/pet/" + petId;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode petNode = objectMapper.readTree(response);

            PetDetails details = parsePetDetails(petNode);
            petDetailsStore.put(petId, details);

            log.info("Stored details for petId={}", petId);

            return ResponseEntity.ok(new FetchPetDetailsResponse("Pet details fetched and stored successfully", petId));
        } catch (Exception e) {
            log.error("Failed to fetch pet details for petId={}", petId, e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pet details fetch failed: " + e.getMessage());
        }
    }

    /*
     * ========== GET /pets/{petId} ==========
     * Returns stored pet details by id
     */
    @GetMapping(value = "/{petId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetDetails> getPetDetails(@PathVariable int petId) {
        PetDetails details = petDetailsStore.get(petId);
        if (details == null) {
            log.warn("Pet details not found for petId={}", petId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet details not found for id: " + petId);
        }
        log.info("Returning stored details for petId={}", petId);
        return ResponseEntity.ok(details);
    }

    /*
     * ========== Exception Handler (minimal) ==========
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getReason()));
    }

    /*
     * ========== Helper Parsing Methods ==========
     */
    private PetSummary parsePetSummary(JsonNode node) {
        PetSummary pet = new PetSummary();
        pet.setId(node.path("id").asInt());
        pet.setName(node.path("name").asText(null));
        pet.setStatus(node.path("status").asText(null));
        pet.setType(node.path("category").path("name").asText(null));

        // photoUrls is array of strings
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            pet.setPhotoUrls(objectMapper.convertValue(node.get("photoUrls"), List.class));
        }
        return pet;
    }

    private PetDetails parsePetDetails(JsonNode node) {
        PetDetails details = new PetDetails();
        details.setId(node.path("id").asInt());
        details.setName(node.path("name").asText(null));
        details.setStatus(node.path("status").asText(null));
        details.setType(node.path("category").path("name").asText(null));

        // Category
        JsonNode catNode = node.path("category");
        if (!catNode.isMissingNode()) {
            Category cat = new Category(catNode.path("id").asInt(), catNode.path("name").asText(null));
            details.setCategory(cat);
        }

        // photoUrls
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            details.setPhotoUrls(objectMapper.convertValue(node.get("photoUrls"), List.class));
        }

        // tags (list)
        if (node.has("tags") && node.get("tags").isArray()) {
            List<Tag> tags = objectMapper.convertValue(node.get("tags"), objectMapper.getTypeFactory().constructCollectionType(List.class, Tag.class));
            details.setTags(tags);
        }

        return details;
    }

    /*
     * ========== DTOs and Models ==========
     */

    @Data
    public static class FilterRequest {
        private String type;
        private String status;
    }

    @Data
    public static class FetchPetsRequest {
        private FilterRequest filter;
    }

    @Data
    @AllArgsConstructor
    public static class FetchPetsResponse {
        private String message;
        private int count;
    }

    @Data
    public static class FetchPetDetailsRequest {
        private int petId;
    }

    @Data
    @AllArgsConstructor
    public static class FetchPetDetailsResponse {
        private String message;
        private int petId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetSummary {
        private int id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetDetails {
        private int id;
        private String name;
        private String type;
        private String status;
        private Category category;
        private List<String> photoUrls;
        private List<Tag> tags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Category {
        private int id;
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Tag {
        private int id;
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
    }
}
```
