package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-entity-prototype")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_PET = "pet";
    private static final String ENTITY_PET_FETCH_REQUEST = "petFetchRequest";

    // --- DTOs ---

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
        private String category;
        private String status;
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


    // ===================== CONTROLLER ENDPOINTS =====================

    @PostMapping("/pets/fetch")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> fetchPets(@RequestBody @Valid FetchRequest request) {

        ObjectNode fetchRequestEntity = objectMapper.createObjectNode();
        fetchRequestEntity.put("sourceUrl", request.getSourceUrl());
        fetchRequestEntity.put("status", request.getStatus());
        fetchRequestEntity.put("requestedAt", Instant.now().toString());

        // Add fetchRequest entity without workflow argument
        return entityService.addItem(
                        ENTITY_PET_FETCH_REQUEST,
                        ENTITY_VERSION,
                        fetchRequestEntity
                )
                .thenApply(id -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("message", "Data fetch started successfully");
                    resp.put("entityId", id.toString());
                    resp.put("requestedAt", Instant.now().toString());
                    return ResponseEntity.accepted().body(resp);
                });
    }

    @PostMapping("/pets/search")
    public CompletableFuture<ResponseEntity<List<Pet>>> searchPets(@RequestBody @Valid SearchRequest request) {
        List<Condition> conditions = new ArrayList<>();

        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            conditions.add(Condition.of("$.category", "IEQUALS", request.getCategory()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            conditions.add(Condition.of("$.status", "IEQUALS", request.getStatus()));
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            conditions.add(Condition.of("$.name", "ICONTAINS", request.getName()));
        }
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            for (String tag : request.getTags()) {
                conditions.add(Condition.of("$.tags", "ICONTAINS", tag));
            }
        }

        SearchConditionRequest conditionRequest = conditions.isEmpty() ? null : SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));

        CompletableFuture<ArrayNode> itemsFuture = (conditionRequest == null)
                ? entityService.getItems(ENTITY_PET, ENTITY_VERSION)
                : entityService.getItemsByCondition(ENTITY_PET, ENTITY_VERSION, conditionRequest);

        return itemsFuture.thenApply(items -> {
            List<Pet> results = new ArrayList<>();
            for (JsonNode node : items) {
                Pet pet = jsonNodeToPet(node);
                if (pet != null) results.add(pet);
            }
            return ResponseEntity.ok(results);
        });
    }

    @GetMapping("/pets/{petId}")
    public CompletableFuture<ResponseEntity<Pet>> getPetById(@PathVariable @NotNull @Positive Long petId) {
        Condition cond = Condition.of("$.id", "EQUALS", petId);
        SearchConditionRequest search = SearchConditionRequest.group("AND", cond);
        return entityService.getItemsByCondition(ENTITY_PET, ENTITY_VERSION, search)
                .thenApply(items -> {
                    if (items.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                    }
                    Pet pet = jsonNodeToPet(items.get(0));
                    return ResponseEntity.ok(pet);
                });
    }

    @GetMapping("/categories")
    public CompletableFuture<ResponseEntity<Set<String>>> getCategories() {
        return entityService.getItems(ENTITY_PET, ENTITY_VERSION)
                .thenApply(items -> {
                    Set<String> categories = new HashSet<>();
                    for (JsonNode node : items) {
                        JsonNode catNode = node.get("category");
                        if (catNode != null && catNode.isTextual()) {
                            categories.add(catNode.asText());
                        }
                    }
                    return ResponseEntity.ok(categories);
                });
    }


    // ===================== WORKFLOW FUNCTIONS =====================

    private CompletableFuture<ObjectNode> processPetFetchRequest(ObjectNode fetchRequestEntity) {
        String sourceUrl = fetchRequestEntity.path("sourceUrl").asText(null);
        String status = fetchRequestEntity.path("status").asText(null);

        if (sourceUrl == null || status == null) {
            logger.warn("Invalid fetchRequestEntity missing sourceUrl or status");
            return CompletableFuture.completedFuture(fetchRequestEntity);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI(sourceUrl + "?status=" + status);
                logger.info("[Workflow] Fetching pets from {}", uri);

                RestTemplate restTemplate = new RestTemplate();
                String raw = restTemplate.getForObject(uri, String.class);

                if (raw == null) {
                    logger.warn("[Workflow] No data fetched from source");
                    return;
                }

                JsonNode root = new ObjectMapper().readTree(raw);
                if (!root.isArray()) {
                    logger.warn("[Workflow] Expected JSON array for pets");
                    return;
                }

                List<ObjectNode> petsToAdd = new ArrayList<>();
                for (JsonNode node : root) {
                    ObjectNode petNode = petJsonNodeToObjectNode(node);
                    if (petNode != null) petsToAdd.add(petNode);
                }

                if (!petsToAdd.isEmpty()) {
                    entityService.addItems(ENTITY_PET, ENTITY_VERSION, petsToAdd, this::processPet).get();
                    logger.info("[Workflow] Added {} pets from fetch", petsToAdd.size());
                }

            } catch (Exception e) {
                logger.error("[Workflow] Error fetching pets", e);
            }
        }).thenApply(v -> fetchRequestEntity);
    }

    private CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        JsonNode statusNode = petEntity.get("status");
        if (statusNode != null && statusNode.isTextual()) {
            petEntity.put("status", statusNode.asText().toLowerCase());
        }
        petEntity.put("lastModified", Instant.now().toString());
        return CompletableFuture.completedFuture(petEntity);
    }


    // ===================== UTILITIES =====================

    private Pet jsonNodeToPet(JsonNode node) {
        try {
            Pet pet = new Pet();
            pet.setId(node.path("id").asLong());
            pet.setName(node.path("name").asText(""));
            pet.setStatus(node.path("status").asText(""));
            pet.setCategory(node.path("category").asText(null));

            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                for (JsonNode t : node.get("tags")) {
                    if (t.isTextual()) tags.add(t.asText());
                }
            }
            pet.setTags(tags);

            List<String> photos = new ArrayList<>();
            if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
                for (JsonNode p : node.get("photoUrls")) {
                    if (p.isTextual()) photos.add(p.asText());
                }
            }
            pet.setPhotoUrls(photos);

            return pet;
        } catch (Exception e) {
            logger.warn("Invalid pet entity: {}", e.getMessage());
            return null;
        }
    }

    private ObjectNode petJsonNodeToObjectNode(JsonNode node) {
        try {
            ObjectNode petNode = objectMapper.createObjectNode();
            petNode.put("id", node.path("id").asLong());
            petNode.put("name", node.path("name").asText(""));
            petNode.put("status", node.path("status").asText(""));
            JsonNode catNode = node.path("category");
            if (catNode.isObject()) {
                petNode.put("category", catNode.path("name").asText(""));
            } else if (catNode.isTextual()) {
                petNode.put("category", catNode.asText());
            }

            ArrayNode tagsArray = objectMapper.createArrayNode();
            JsonNode tagsNode = node.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode tag : tagsNode) {
                    if (tag.isObject()) {
                        String tagName = tag.path("name").asText(null);
                        if (tagName != null) tagsArray.add(tagName);
                    } else if (tag.isTextual()) {
                        tagsArray.add(tag.asText());
                    }
                }
            }
            petNode.set("tags", tagsArray);

            ArrayNode photosArray = objectMapper.createArrayNode();
            JsonNode photosNode = node.path("photoUrls");
            if (photosNode.isArray()) {
                for (JsonNode p : photosNode) {
                    if (p.isTextual()) photosArray.add(p.asText());
                }
            }
            petNode.set("photoUrls", photosArray);

            return petNode;
        } catch (Exception e) {
            logger.warn("Failed to convert pet node: {}", e.getMessage());
            return null;
        }
    }


    // ===================== EXCEPTION HANDLERS =====================

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