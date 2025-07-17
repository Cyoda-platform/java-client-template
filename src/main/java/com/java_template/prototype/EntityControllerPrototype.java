package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@Validated
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<String, Pet> petStore = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    @Data
    public static class PetSyncRequest {
        @NotBlank(message = "source is required")
        private String source;
        @NotBlank(message = "action is required")
        @Pattern(regexp = "(?i)sync|update", message = "action must be 'sync' or 'update'")
        private String action;
        @NotNull(message = "petData is required")
        @Valid
        private Pet petData;
    }

    @Data
    public static class Pet {
        @NotBlank(message = "id is required")
        private String id;
        @NotBlank(message = "name is required")
        private String name;
        @NotBlank(message = "category is required")
        private String category;
        @Size(min = 1, message = "at least one photoUrl is required")
        private List<String> photoUrls;
        @Size(min = 1, message = "at least one tag is required")
        private List<String> tags;
        @NotBlank(message = "status is required")
        @Pattern(regexp = "(?i)available|pending|sold", message = "status must be 'available', 'pending', or 'sold'")
        private String status;
    }

    @Data
    public static class PetSearchRequest {
        @NotBlank(message = "status is required")
        @Pattern(regexp = "(?i)available|pending|sold", message = "status must be 'available', 'pending', or 'sold'")
        private String status;
        private String category;
        private List<String> tags;
        private String nameContains;
    }

    @Data
    public static class PetRecommendationResponse {
        private String recommendation;
    }

    @Data
    public static class JobStatus {
        private String status;
        private Instant requestedAt;
        public JobStatus(String status, Instant requestedAt) {
            this.status = status;
            this.requestedAt = requestedAt;
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncPetData(@RequestBody @Valid PetSyncRequest request) {
        logger.info("Received sync request: source={}, action={}", request.getSource(), request.getAction());
        if (!"petstore".equalsIgnoreCase(request.getSource())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source");
        }
        if ("sync".equalsIgnoreCase(request.getAction())) {
            CompletableFuture.runAsync(() -> {
                try {
                    String externalPetId = "1"; // TODO: replace with dynamic IDs or bulk fetch
                    logger.info("Fetching pet id={} from external Petstore API", externalPetId);
                    String url = PETSTORE_API_BASE + "/" + externalPetId;
                    String jsonResponse = restTemplate.getForObject(url, String.class);
                    JsonNode root = objectMapper.readTree(jsonResponse);
                    Pet pet = mapJsonNodeToPet(root);
                    petStore.put(pet.getId(), pet);
                    logger.info("Synced pet id={} into internal store", pet.getId());
                } catch (Exception e) {
                    logger.error("Failed to sync pets from external API", e);
                }
            }, executor);
            return ResponseEntity.ok(Map.of("success", true, "message", "Pet data sync started in background"));
        } else {
            Pet pet = request.getPetData();
            petStore.put(pet.getId(), pet);
            logger.info("Updated pet id={} in internal store", pet.getId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Pet data updated", "updatedPetId", pet.getId()));
        }
    }

    @GetMapping("/{petId}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String petId) {
        logger.info("Fetching pet details for id={}", petId);
        Pet pet = petStore.get(petId);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid PetSearchRequest request) {
        logger.info("Searching pets with status={}", request.getStatus());
        try {
            String url = PETSTORE_API_BASE + "/findByStatus?status=" + request.getStatus();
            String jsonResponse = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(jsonResponse);
            List<Pet> matchedPets = new CopyOnWriteArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    Pet pet = mapJsonNodeToPet(node);
                    if (matchesFilter(pet, request)) {
                        matchedPets.add(pet);
                    }
                }
            }
            matchedPets.forEach(p -> petStore.put(p.getId(), p));
            logger.info("Search returned {} pets", matchedPets.size());
            return ResponseEntity.ok(matchedPets);
        } catch (Exception e) {
            logger.error("Failed to search pets from external API", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets");
        }
    }

    @PostMapping("/{petId}/recommend")
    public ResponseEntity<PetRecommendationResponse> recommendPet(@PathVariable @NotBlank String petId) {
        logger.info("Generating recommendation for pet id={}", petId);
        Pet pet = petStore.get(petId);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        String recommendation = String.format("We think %s would love a walk in the park today! 🐾", pet.getName());
        PetRecommendationResponse response = new PetRecommendationResponse();
        response.setRecommendation(recommendation);
        logger.info("Recommendation generated for pet id={}", petId);
        return ResponseEntity.ok(response);
    }

    private Pet mapJsonNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        pet.setId(node.path("id").asText());
        pet.setName(node.path("name").asText());
        JsonNode categoryNode = node.path("category");
        if (!categoryNode.isMissingNode()) {
            pet.setCategory(categoryNode.path("name").asText());
        }
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            pet.setPhotoUrls(objectMapper.convertValue(node.get("photoUrls"), List.class));
        }
        if (node.has("tags") && node.get("tags").isArray()) {
            List<String> tags = new CopyOnWriteArrayList<>();
            for (JsonNode tagNode : node.get("tags")) {
                String tagName = tagNode.path("name").asText();
                if (tagName != null) tags.add(tagName);
            }
            pet.setTags(tags);
        }
        pet.setStatus(node.path("status").asText());
        return pet;
    }

    private boolean matchesFilter(Pet pet, PetSearchRequest filter) {
        if (filter.getCategory() != null && !filter.getCategory().equalsIgnoreCase(pet.getCategory())) return false;
        if (filter.getTags() != null && !filter.getTags().isEmpty()) {
            if (pet.getTags() == null || !pet.getTags().containsAll(filter.getTags())) return false;
        }
        if (filter.getNameContains() != null && !pet.getName().toLowerCase().contains(filter.getNameContains().toLowerCase())) return false;
        return true;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }
}