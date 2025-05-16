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
import org.springframework.util.StringUtils;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory store for pets keyed by ID (mock persistence)
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();

    // Simple job tracking map for fetch jobs if needed
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status=%s";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    /**
     * POST /pets/fetch
     * Fetch pet data from external Petstore API based on filters. Only 'status' supported externally.
     */
    @PostMapping(path = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public FetchResponse fetchPets(@RequestBody FetchRequest request) {
        log.info("Received fetch request: {}", request);

        // Validate input (only status supported externally)
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "available";

        try {
            // External call to Petstore API by status
            String url = String.format(PETSTORE_API_URL, status);
            String jsonResponse = restTemplate.getForObject(url, String.class);

            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            if (!rootNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected API response format");
            }

            // Process and store pets internally (limit if requested)
            int limit = (request.getLimit() != null && request.getLimit() > 0) ? request.getLimit() : rootNode.size();
            int fetchedCount = 0;

            for (int i = 0; i < Math.min(limit, rootNode.size()); i++) {
                JsonNode petNode = rootNode.get(i);
                Pet pet = jsonNodeToPet(petNode);
                if (pet != null) {
                    petStore.put(pet.getId(), pet);
                    fetchedCount++;
                }
            }

            // TODO: trigger Cyoda workflows here if needed (fire-and-forget)
            CompletableFuture.runAsync(() -> {
                log.info("Triggering workflows for fetched pets (mocked)");
                // mock workflow trigger
            });

            log.info("Fetched and stored {} pets", fetchedCount);
            return new FetchResponse(fetchedCount, "Pets fetched and stored successfully");

        } catch (Exception e) {
            log.error("Error fetching pets from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    /**
     * GET /pets
     * Retrieve stored pet data with optional query filters.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> getPets(@RequestParam(required = false) String type,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) Integer limit) {
        log.info("Received get pets request with filters: type={}, status={}, limit={}", type, status, limit);

        List<Pet> filtered = new ArrayList<>();
        for (Pet pet : petStore.values()) {
            if ((type == null || type.equalsIgnoreCase(pet.getType())) &&
                (status == null || status.equalsIgnoreCase(pet.getStatus()))) {
                filtered.add(pet);
            }
        }

        if (limit != null && limit > 0 && filtered.size() > limit) {
            filtered = filtered.subList(0, limit);
        }

        log.info("Returning {} pets after filtering", filtered.size());
        return filtered;
    }

    /**
     * POST /pets/recommendation
     * Return random or filtered pet recommendations.
     */
    @PostMapping(path = "/recommendation", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> getRecommendations(@RequestBody RecommendationRequest request) {
        log.info("Received recommendation request: {}", request);

        List<Pet> candidates = new ArrayList<>();
        for (Pet pet : petStore.values()) {
            if (request.getPreferredType() == null || request.getPreferredType().equalsIgnoreCase(pet.getType())) {
                candidates.add(pet);
            }
        }

        // Randomize and limit results
        Collections.shuffle(candidates);
        int maxResults = (request.getMaxResults() != null && request.getMaxResults() > 0) ? request.getMaxResults() : 5;
        if (candidates.size() > maxResults) {
            candidates = candidates.subList(0, maxResults);
        }

        log.info("Returning {} recommended pets", candidates.size());
        return candidates;
    }

    private Pet jsonNodeToPet(JsonNode node) {
        try {
            Long id = node.path("id").asLong();
            String name = node.path("name").asText(null);
            String type = node.path("category").path("name").asText(null);
            String status = node.path("status").asText(null);

            List<String> photoUrls = new ArrayList<>();
            JsonNode photosNode = node.path("photoUrls");
            if (photosNode.isArray()) {
                for (JsonNode urlNode : photosNode) {
                    photoUrls.add(urlNode.asText());
                }
            }

            return new Pet(id, name, type, status, photoUrls);
        } catch (Exception e) {
            log.error("Failed to parse pet JSON node: {}", node, e);
            return null;
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, String> handleResponseStatusException(ResponseStatusException ex) {
        log.error("ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = new HashMap<>();
        error.put("error", ex.getReason());
        error.put("status", String.valueOf(ex.getStatusCode().value()));
        return error;
    }

    // --- DTOs and inner classes ---

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchRequest {
        private String type;    // currently ignored for external fetch - Petstore API supports only status query
        private String status;
        private Integer limit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchResponse {
        private int fetchedCount;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationRequest {
        private String preferredType;
        private Integer maxResults;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class JobStatus {
        private String status;
        private Instant requestedAt;
    }
}
```