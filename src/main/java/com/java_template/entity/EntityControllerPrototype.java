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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SearchResult> searchResults = new ConcurrentHashMap<>();
    private static final String EXTERNAL_PET_API_FIND_BY_STATUS =
            "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    @PostConstruct
    public void init() {
        log.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody @Valid PetSearchRequest request) {
        log.info("Received search request: {}", request);

        String searchId = UUID.randomUUID().toString();
        Instant requestedAt = Instant.now();
        searchResults.put(searchId, new SearchResult(Collections.emptyList(), "", requestedAt));

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

    @GetMapping("/results/{searchId}")
    public ResponseEntity<SearchResult> getSearchResult(@PathVariable @NotBlank String searchId) {
        log.info("Fetching results for searchId={}", searchId);
        SearchResult result = searchResults.get(searchId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Search ID not found");
        }
        return ResponseEntity.ok(result);
    }

    private void fetchTransformAndStore(String searchId, PetSearchRequest request) {
        log.info("Start processing fetch-transform-store for searchId={}", searchId);
        try {
            List<JsonNode> externalPets = fetchFromExternalAPI(request);
            List<TransformedPet> transformedPets = transformPets(externalPets);
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

    private List<JsonNode> fetchFromExternalAPI(PetSearchRequest request) throws Exception {
        String statusQuery = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "available";
        URI uri = new URI(EXTERNAL_PET_API_FIND_BY_STATUS.replace("{status}", statusQuery));
        String responseBody = restTemplate.getForObject(uri, String.class);
        if (responseBody == null) {
            return Collections.emptyList();
        }
        JsonNode rootNode = objectMapper.readTree(responseBody);
        if (!rootNode.isArray()) {
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

    private List<TransformedPet> transformPets(List<JsonNode> pets) {
        List<TransformedPet> transformed = new ArrayList<>();
        for (JsonNode pet : pets) {
            String name = pet.path("name").asText("");
            String species = pet.path("species").asText("");
            int categoryId = pet.path("category").path("id").asInt(-1);
            String status = pet.path("status").asText("unknown");
            TransformedPet tp = new TransformedPet();
            tp.setName(name);
            tp.setSpecies(species);
            tp.setCategoryId(categoryId);
            tp.setAvailability(status);
            transformed.add(tp);
        }
        return transformed;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetSearchRequest {
        @Size(min = 1, max = 50)
        private String species;
        @Size(min = 1, max = 50)
        private String status;
        @PositiveOrZero
        private Integer categoryId;
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
        @NotBlank
        private String name;
        @NotBlank
        private String species;
        @PositiveOrZero
        private Integer categoryId;
        @NotBlank
        private String availability;
    }

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