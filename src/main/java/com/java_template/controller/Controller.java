package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping(path = "/prototype/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    public Controller(EntityService entityService, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    // DTOs moved from prototype for request/response binding as no separate entity package found

    public static class PetSyncRequest {
        @NotBlank(message = "source is required")
        private String source;

        @NotBlank(message = "action is required")
        @Pattern(regexp = "(?i)sync|update", message = "action must be 'sync' or 'update'")
        private String action;

        @NotNull(message = "petData is required")
        @Valid
        private Pet petData;

        // getters and setters
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Pet getPetData() { return petData; }
        public void setPetData(Pet petData) { this.petData = petData; }
    }

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

        // getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public List<String> getPhotoUrls() { return photoUrls; }
        public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class PetSearchRequest {
        @NotBlank(message = "status is required")
        @Pattern(regexp = "(?i)available|pending|sold", message = "status must be 'available', 'pending', or 'sold'")
        private String status;

        private String category;

        private List<String> tags;

        private String nameContains;

        // getters and setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public String getNameContains() { return nameContains; }
        public void setNameContains(String nameContains) { this.nameContains = nameContains; }
    }

    public static class PetRecommendationResponse {
        private String recommendation;

        // getter and setter
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
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
                    String externalPetId = "1"; // static example, could be dynamic or batch
                    logger.info("Fetching pet id={} from external Petstore API", externalPetId);
                    String url = PETSTORE_API_BASE + "/" + externalPetId;
                    String jsonResponse = restTemplate.getForObject(url, String.class);
                    JsonNode root = objectMapper.readTree(jsonResponse);
                    Pet pet = mapJsonNodeToPet(root);
                    entityService.addItem("Pet", ENTITY_VERSION, pet).join();
                    logger.info("Synced pet id={} into external entity service", pet.getId());
                } catch (Exception e) {
                    logger.error("Failed to sync pets from external API", e);
                }
            });
            return ResponseEntity.ok(Map.of("success", true, "message", "Pet data sync started in background"));
        } else {
            Pet pet = request.getPetData();
            entityService.updateItem("Pet", ENTITY_VERSION, pet.getId(), pet).join();
            logger.info("Updated pet id={} in external entity service", pet.getId());
            return ResponseEntity.ok(Map.of("success", true, "message", "Pet data updated", "updatedPetId", pet.getId()));
        }
    }

    @GetMapping("/{petId}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String petId) {
        logger.info("Fetching pet details for id={}", petId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", petId));
        ArrayNode items = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition).join();
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        ObjectNode node = (ObjectNode) items.get(0);
        Pet pet = objectMapper.convertValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid PetSearchRequest request) {
        logger.info("Searching pets with status={}", request.getStatus());
        try {
            List<Condition> conditions = new ArrayList<>();
            conditions.add(Condition.of("$.status", "IEQUALS", request.getStatus()));

            if (request.getCategory() != null && !request.getCategory().isEmpty()) {
                conditions.add(Condition.of("$.category", "IEQUALS", request.getCategory()));
            }
            if (request.getTags() != null && !request.getTags().isEmpty()) {
                for (String tag : request.getTags()) {
                    conditions.add(Condition.of("$.tags", "INOT_CONTAINS", tag));
                }
            }
            if (request.getNameContains() != null && !request.getNameContains().isEmpty()) {
                conditions.add(Condition.of("$.name", "ICONTAINS", request.getNameContains()));
            }
            SearchConditionRequest condition = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));

            ArrayNode items = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition).join();
            List<Pet> matchedPets = new CopyOnWriteArrayList<>();
            for (JsonNode node : items) {
                Pet pet = objectMapper.convertValue(node, Pet.class);
                matchedPets.add(pet);
            }
            logger.info("Search returned {} pets", matchedPets.size());
            return ResponseEntity.ok(matchedPets);
        } catch (Exception e) {
            logger.error("Failed to search pets from external entity service", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets");
        }
    }

    @PostMapping("/{petId}/recommend")
    public ResponseEntity<PetRecommendationResponse> recommendPet(@PathVariable @NotBlank String petId) {
        logger.info("Generating recommendation for pet id={}", petId);
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", petId));
        ArrayNode items = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition).join();
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.convertValue(items.get(0), Pet.class);
        String recommendation = String.format("We think %s would love a walk in the park today! \uD83D\uDC3E", pet.getName());
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

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }
}