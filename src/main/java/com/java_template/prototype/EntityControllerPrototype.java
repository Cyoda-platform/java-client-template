package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/prototype")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petsStore = new ConcurrentHashMap<>();
    private final Set<String> categoriesStore = Collections.synchronizedSet(new HashSet<>());

    @Data
    public static class FetchRequest {
        @NotBlank
        @Pattern(regexp = "https?://.+", message = "Must be a valid URL")
        private String sourceUrl;
        @NotBlank
        private String status;
    }

    @Data
    public static class SearchRequest {
        @Size(min = 1)
        private String category;
        @Size(min = 1)
        private String status;
        @Size(min = 1)
        private String name;
        @Size(min = 1)
        private List<@NotBlank String> tags;
    }

    @Data
    public static class Pet {
        @NotNull
        @Positive
        private Long id;
        @NotBlank
        private String name;
        private String category;
        private String status;
        private List<String> tags;
        private List<String> photoUrls;
    }

    @PostMapping("/pets/fetch")
    public ResponseEntity<Map<String, Object>> fetchPets(@RequestBody @Valid FetchRequest request) {
        logger.info("Received fetch request: status='{}', sourceUrl='{}'", request.getStatus(), request.getSourceUrl());
        CompletableFuture.runAsync(() -> fetchAndStorePets(request.getSourceUrl(), request.getStatus()));
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Data fetch started successfully");
        resp.put("requestedAt", Instant.now().toString());
        return ResponseEntity.accepted().body(resp);
    }

    @Async
    public void fetchAndStorePets(String sourceUrl, String status) {
        try {
            URI uri = new URI(sourceUrl + "?status=" + status);
            logger.info("Fetching from {}", uri);
            String raw = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(raw);
            if (!root.isArray()) {
                logger.error("Expected JSON array");
                return;
            }
            int count = 0;
            for (JsonNode node : root) {
                Pet pet = jsonNodeToPet(node);
                if (pet != null) {
                    petsStore.put(pet.getId(), pet);
                    if (pet.getCategory() != null) categoriesStore.add(pet.getCategory());
                    count++;
                }
            }
            logger.info("Stored {} pets", count);
        } catch (Exception e) {
            logger.error("Error in fetchAndStorePets", e);
        }
    }

    private Pet jsonNodeToPet(JsonNode node) {
        try {
            Pet pet = new Pet();
            pet.setId(node.path("id").asLong());
            pet.setName(node.path("name").asText(""));
            pet.setStatus(node.path("status").asText(""));
            JsonNode cat = node.path("category");
            pet.setCategory(cat.isObject() ? cat.path("name").asText("") : null);
            List<String> tags = new ArrayList<>();
            for (JsonNode t : node.path("tags")) {
                String n = t.path("name").asText(null);
                if (n != null) tags.add(n);
            }
            pet.setTags(tags);
            List<String> photos = new ArrayList<>();
            for (JsonNode p : node.path("photoUrls")) {
                if (p.isTextual()) photos.add(p.asText());
            }
            pet.setPhotoUrls(photos);
            return pet;
        } catch (Exception e) {
            logger.error("Failed to parse pet node", e);
            return null;
        }
    }

    @PostMapping("/pets/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("Search with filters: category='{}', status='{}', name='{}', tags={}",
                request.getCategory(), request.getStatus(), request.getName(), request.getTags());
        List<Pet> results = new ArrayList<>();
        for (Pet pet : petsStore.values()) {
            if (matches(pet, request)) results.add(pet);
        }
        logger.info("Found {} pets", results.size());
        return ResponseEntity.ok(results);
    }

    private boolean matches(Pet pet, SearchRequest f) {
        if (f.getCategory() != null && !f.getCategory().equalsIgnoreCase(pet.getCategory())) return false;
        if (f.getStatus() != null && !f.getStatus().equalsIgnoreCase(pet.getStatus())) return false;
        if (f.getName() != null && !pet.getName().toLowerCase().contains(f.getName().toLowerCase())) return false;
        if (f.getTags() != null && !pet.getTags().containsAll(f.getTags())) return false;
        return true;
    }

    @GetMapping("/pets/{petId}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotNull @Positive Long petId) {
        logger.info("Get pet id={}", petId);
        Pet pet = petsStore.get(petId);
        if (pet == null) {
            logger.error("Pet not found id={}", petId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @GetMapping("/categories")
    public ResponseEntity<Set<String>> getCategories() {
        logger.info("Get categories");
        return ResponseEntity.ok(new HashSet<>(categoriesStore));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        logger.error("StatusException: {}", ex.getStatusCode());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Internal server error");
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}