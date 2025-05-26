package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-entity")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CyodaEntityControllerPrototype(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId; // from entityService, mapped to technicalId
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    static class SearchRequest {
        @Size(min = 1, max = 30)
        private String type;

        @Pattern(regexp = "available|pending|sold", message = "status must be available, pending, or sold")
        private String status;

        @Size(max = 5)
        private List<@Size(min = 1, max = 20) String> tags;
    }

    @Data
    @NoArgsConstructor
    static class FavoriteRequest {
        @NotNull
        private UUID technicalId;
    }

    @Data
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }

    private final Map<String, List<Pet>> lastSearchCache = new HashMap<>();
    private final Map<String, Map<UUID, Pet>> userFavorites = new HashMap<>();
    private static final String DEFAULT_USER = "defaultUser";

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Map<String, List<Pet>>>> searchPets(@RequestBody @Valid SearchRequest request) throws Exception {
        logger.info("Received search request: type={}, status={}, tags={}", request.getType(), request.getStatus(), request.getTags());

        String statusParam = (request.getStatus() == null || request.getStatus().isBlank()) ? "available" : request.getStatus();
        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusParam;
        logger.info("Querying external Petstore API: {}", url);

        String response = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
        JsonNode root = objectMapper.readTree(response);
        if (!root.isArray()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected response format from Petstore API");
        }
        List<Pet> pets = new ArrayList<>();
        for (JsonNode petNode : root) {
            Pet pet = parsePetFromJsonNode(petNode);
            if (pet == null) continue;
            if (request.getType() != null && !request.getType().isBlank() && !request.getType().equalsIgnoreCase(pet.getType())) {
                continue;
            }
            if (request.getTags() != null && !request.getTags().isEmpty() && (pet.getTags() == null || !pet.getTags().containsAll(request.getTags()))) {
                continue;
            }
            pets.add(pet);
        }

        lastSearchCache.put(DEFAULT_USER, pets);

        Map<String, List<Pet>> resp = new HashMap<>();
        resp.put("pets", pets);
        logger.info("Returning {} pets from search", pets.size());

        return CompletableFuture.completedFuture(ResponseEntity.ok(resp));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<ResponseEntity<Map<String, List<Pet>>>> getLastSearchPets() {
        List<Pet> pets = lastSearchCache.getOrDefault(DEFAULT_USER, Collections.emptyList());
        Map<String, List<Pet>> resp = new HashMap<>();
        resp.put("pets", pets);
        logger.info("Returning {} cached pets for last search", pets.size());
        return CompletableFuture.completedFuture(ResponseEntity.ok(resp));
    }

    @PostMapping(value = "/favorite", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> addFavorite(@RequestBody @Valid FavoriteRequest request) {
        logger.info("Adding technicalId={} to favorites for user={}", request.getTechnicalId(), DEFAULT_USER);

        Pet pet = userFavorites.getOrDefault(DEFAULT_USER, Collections.emptyMap()).get(request.getTechnicalId());
        if (pet == null) {
            ObjectNode entityNode = entityService.getItem("pet", ENTITY_VERSION, request.getTechnicalId()).join();
            if (entityNode == null) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet with given technicalId not found");
            }
            pet = mapObjectNodeToPet(entityNode);
            if (pet == null) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Failed to map pet entity");
            }
            userFavorites.computeIfAbsent(DEFAULT_USER, k -> new HashMap<>()).put(request.getTechnicalId(), pet);
        }

        logger.info("Pet {} added to favorites", request.getTechnicalId());
        return ResponseEntity.ok(new MessageResponse("Pet added to favorites"));
    }

    @GetMapping(value = "/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Collection<Pet>>> getFavorites() {
        Collection<Pet> favorites = userFavorites.getOrDefault(DEFAULT_USER, Collections.emptyMap()).values();
        Map<String, Collection<Pet>> resp = new HashMap<>();
        resp.put("favorites", favorites);
        logger.info("Returning {} favorite pets for user", favorites.size());
        return ResponseEntity.ok(resp);
    }

    public CompletableFuture<UUID> addPetWithWorkflow(Pet pet) {
        try {
            ObjectNode petNode = objectMapper.valueToTree(pet);
            return entityService.addItem(
                    "pet",
                    ENTITY_VERSION,
                    petNode,
                    this::processPet
            );
        } catch (Exception e) {
            logger.error("Error adding pet with workflow", e);
            CompletableFuture<UUID> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(e);
            return failedFuture;
        }
    }

    private CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        logger.info("Starting processPet workflow for entity: {}", petEntity);

        if (!petEntity.hasNonNull("status") || petEntity.get("status").asText().isBlank()) {
            petEntity.put("status", "available");
        }

        if (!petEntity.hasNonNull("createdAt")) {
            petEntity.put("createdAt", Instant.now().toString());
        }

        if (!petEntity.has("tags") || !petEntity.get("tags").isArray() || petEntity.get("tags").size() == 0) {
            ArrayNode tagsArray = petEntity.putArray("tags");
            tagsArray.add("new-pet");
        }

        CompletableFuture<Void> asyncEnrichCategory = CompletableFuture.runAsync(() -> {
            try {
                String type = petEntity.hasNonNull("type") ? petEntity.get("type").asText() : null;
                if (type != null && !type.isBlank()) {
                    logger.info("Would enrich pet with category entity for type '{}'", type);
                    // Example: fetch category entities and enrich petEntity here if needed
                    // No add/update/delete of 'pet' entity to avoid recursion
                }
            } catch (Exception e) {
                logger.warn("Failed to enrich category in processPet workflow", e);
            }
        });

        CompletableFuture<Void> fireAndForget = CompletableFuture.runAsync(() -> {
            try {
                logger.info("Fire and forget async task: audit log for pet: {}", petEntity);
                // Example: send audit log or notification asynchronously
            } catch (Exception e) {
                logger.warn("Fire and forget task failed", e);
            }
        });

        return CompletableFuture.allOf(asyncEnrichCategory, fireAndForget)
                .thenApply(v -> petEntity);
    }

    private Pet parsePetFromJsonNode(JsonNode petNode) {
        try {
            Long id = petNode.has("id") && !petNode.get("id").isNull() ? petNode.get("id").asLong() : null;
            String name = petNode.has("name") && !petNode.get("name").isNull() ? petNode.get("name").asText() : null;
            String status = petNode.has("status") && !petNode.get("status").isNull() ? petNode.get("status").asText() : null;
            String type = null;
            if (petNode.has("category") && !petNode.get("category").isNull()) {
                JsonNode categoryNode = petNode.get("category");
                if (categoryNode.has("name") && !categoryNode.get("name").isNull()) {
                    type = categoryNode.get("name").asText();
                } else if (categoryNode.isTextual()) {
                    type = categoryNode.asText();
                }
            }
            List<String> tags = new ArrayList<>();
            if (petNode.has("tags") && petNode.get("tags").isArray()) {
                for (JsonNode tagNode : petNode.get("tags")) {
                    if (tagNode.has("name") && !tagNode.get("name").isNull()) {
                        tags.add(tagNode.get("name").asText());
                    }
                }
            }
            return new Pet(null, id, name, type, status, tags);
        } catch (Exception e) {
            logger.error("Failed to parse pet from JSON node", e);
            return null;
        }
    }

    private Pet mapObjectNodeToPet(ObjectNode node) {
        try {
            UUID technicalId = node.has("technicalId") && !node.get("technicalId").isNull() ? UUID.fromString(node.get("technicalId").asText()) : null;
            Long id = node.has("id") && !node.get("id").isNull() ? node.get("id").asLong() : null;
            String name = node.has("name") && !node.get("name").isNull() ? node.get("name").asText() : null;
            String type = node.has("type") && !node.get("type").isNull() ? node.get("type").asText() : null;
            String status = node.has("status") && !node.get("status").isNull() ? node.get("status").asText() : null;
            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                for (JsonNode tagNode : node.get("tags")) {
                    if (!tagNode.isNull()) {
                        tags.add(tagNode.asText());
                    }
                }
            }
            return new Pet(technicalId, id, name, type, status, tags);
        } catch (Exception e) {
            logger.error("Failed to map ObjectNode to Pet", e);
            return null;
        }
    }
}