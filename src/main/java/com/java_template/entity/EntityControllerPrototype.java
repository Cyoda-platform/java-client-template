```java
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Store search results indexed by searchId
    private final Map<String, SearchResult> searchResults = new ConcurrentHashMap<>();

    // External API base URL (from provided SwaggerHub API)
    private static final String EXTERNAL_PET_API_FIND_BY_STATUS =
            "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    /**
     * POST /api/pets/search
     * Trigger search with given filters, fetch from external API, transform and store results.
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody PetSearchRequest request) {
        log.info("Received search request: {}", request);

        // Validate inputs - all optional, but if provided check non-empty
        if (request.getSpecies() != null && !StringUtils.hasText(request.getSpecies())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "species cannot be empty");
        }
        if (request.getStatus() != null && !StringUtils.hasText(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status cannot be empty");
        }
        // categoryId can be null or positive integer
        if (request.getCategoryId() != null && request.getCategoryId() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryId cannot be negative");
        }

        String searchId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();

        // Mark job as processing with empty results initially
        searchResults.put(searchId, new SearchResult(Collections.emptyList(), "", requestedAt));

        // Fire-and-forget async processing
        CompletableFuture.runAsync(() -> fetchTransformAndStore(searchId, request))
                .exceptionally(ex -> {
                    log.error("Failed processing searchId={} : {}", searchId, ex.toString());
                    searchResults.put(searchId,
                            new SearchResult(Collections.emptyList(), "Processing failed", requestedAt));
                    return null;
                });

        log.info("Search initiated with searchId={}", searchId);
        return ResponseEntity.ok(new SearchResponse(searchId, "Search initiated"));
    }

    /**
     * GET /api/pets/results/{searchId}
     * Retrieve transformed pet list by searchId.
     */
    @GetMapping("/results/{searchId}")
    public ResponseEntity<SearchResult> getSearchResult(@PathVariable String searchId) {
        log.info("Fetching results for searchId={}", searchId);
        SearchResult result = searchResults.get(searchId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search ID not found");
        }
        return ResponseEntity.ok(result);
    }

    // --- Internal methods ---

    /**
     * Fetch pet data from external API, transform it, and store in searchResults map.
     */
    private void fetchTransformAndStore(String searchId, PetSearchRequest request) {
        log.info("Start processing fetch-transform-store for searchId={}", searchId);

        try {
            List<JsonNode> externalPets = fetchFromExternalAPI(request);

            List<TransformedPet> transformedPets = transformPets(externalPets, request);

            String notification = transformedPets.isEmpty() ? "No pets found" : "";

            SearchResult finalResult = new SearchResult(transformedPets, notification, Instant.now());
            searchResults.put(searchId, finalResult);

            log.info("Stored {} transformed pets for searchId={}", transformedPets.size(), searchId);
        } catch (Exception e) {
            log.error("Error during fetch-transform-store for searchId={}: {}", searchId, e.toString());
            searchResults.put(searchId,
                    new SearchResult(Collections.emptyList(), "Failed to fetch or process data", Instant.now()));
        }
    }

    /**
     * Call external pet API to fetch pets by status, then filter by species and categoryId locally
     * because the external API supports filtering only by status.
     */
    private List<JsonNode> fetchFromExternalAPI(PetSearchRequest request) throws Exception {
        log.info("Calling external API with status={}", request.getStatus());

        String statusQuery = request.getStatus();
        if (!StringUtils.hasText(statusQuery)) {
            // If no status provided, default to "available" to prevent empty query (per API docs)
            statusQuery = "available";
        }

        URI uri = new URI(EXTERNAL_PET_API_FIND_BY_STATUS.replace("{status}", statusQuery));

        String responseBody = restTemplate.getForObject(uri, String.class);
        if (responseBody == null) {
            log.warn("External API returned empty body");
            return Collections.emptyList();
        }

        JsonNode rootNode = objectMapper.readTree(responseBody);
        if (!rootNode.isArray()) {
            log.warn("External API returned unexpected data type (expected array)");
            return Collections.emptyList();
        }

        List<JsonNode> filteredPets = new ArrayList<>();
        for (JsonNode petNode : rootNode) {
            boolean matchesSpecies = true;
            boolean matchesCategory = true;

            if (StringUtils.hasText(request.getSpecies())) {
                String petSpecies = petNode.path("species").asText(null);
                matchesSpecies = request.getSpecies().equalsIgnoreCase(petSpecies);
            }

            if (request.getCategoryId() != null) {
                int petCategoryId = petNode.path("category").path("id").asInt(-1);
                matchesCategory = (petCategoryId == request.getCategoryId());
            }

            if (matchesSpecies && matchesCategory) {
                filteredPets.add(petNode);
            }
        }
        log.info("Filtered pets count after species/category filtering: {}", filteredPets.size());
        return filteredPets;
    }

    /**
     * Transform raw pet JsonNode list into TransformedPet list with renamed fields and extra attributes.
     */
    private List<TransformedPet> transformPets(List<JsonNode> pets, PetSearchRequest request) {
        List<TransformedPet> transformed = new ArrayList<>();

        for (JsonNode pet : pets) {
            // Rename "name" to "Name" (assuming external API uses 'name' field)
            String name = pet.path("name").asText("");
            String species = pet.path("species").asText(""); // may be missing, so default empty
            int categoryId = pet.path("category").path("id").asInt(-1);
            String status = pet.path("status").asText("unknown");

            TransformedPet tp = new TransformedPet();
            tp.setName(name);
            tp.setSpecies(species);
            tp.setCategoryId(categoryId);
            tp.setAvailability(status);

            // Copy any other attributes you want as needed, here left empty for prototype
            // TODO: add other attribute mappings if needed

            transformed.add(tp);
        }
        return transformed;
    }

    // --- DTO classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetSearchRequest {
        private String species;      // optional
        private String status;       // optional
        private Integer categoryId;  // optional
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private String searchId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResult {
        private List<TransformedPet> pets;
        private String notification;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransformedPet {
        private String Name;
        private String Species;
        private Integer CategoryId;
        private String Availability;

        // TODO: add other attributes if needed
    }

    // --- Minimal Exception Handler ---

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled exception: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```
