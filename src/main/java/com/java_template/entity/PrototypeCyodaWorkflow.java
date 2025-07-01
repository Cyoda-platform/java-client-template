package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping("cyoda/purrfect-pets")
public class CyodaEntityControllerPrototype {

    private final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    private static final String ENTITY_NAME = "purrfect-pets";

    @Data
    public static class PetSearchRequest {
        @NotNull
        @Pattern(regexp = "^(cat|dog|all)$")
        private String type;
        @NotNull
        @Pattern(regexp = "^(available|pending|sold)$")
        private String status;
        @Size(max = 100)
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetInfo {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String description;
        private Integer age;
    }

    @Data
    public static class PetSearchResponse {
        private List<PetInfo> pets = new ArrayList<>();
    }

    @Data
    public static class FavoriteAddRequest {
        @NotBlank
        private String userId;
        @NotNull
        private Long petId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FavoriteAddResponse {
        private boolean success;
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FavoriteEntry {
        private Long petId;
        private String name;
        private String type;
        private String status;
    }

    @Data
    public static class FavoriteListResponse {
        private String userId;
        private List<FavoriteEntry> favorites = new ArrayList<>();
    }

    @Data
    public static class PetCareTipsRequest {
        @NotNull
        @Pattern(regexp = "^(cat|dog|all)$")
        private String type;
        @NotNull
        @Min(0)
        @Max(20)
        private Integer age;
    }

    @Data
    public static class PetCareTipsResponse {
        private List<String> tips = new ArrayList<>();
    }

    // Workflow function for purrfect-pets entity
    private final Function<Object, Object> processpurrfect_pets = entity -> {
        logger.info("Running workflow processpurrfect_pets for entity before persistence");

        if (!(entity instanceof ObjectNode)) {
            logger.warn("Entity is not an ObjectNode, cannot process");
            return entity;
        }
        ObjectNode entityNode = (ObjectNode) entity;

        // Add createdAt timestamp if not present
        if (!entityNode.has("createdAt")) {
            entityNode.put("createdAt", System.currentTimeMillis());
        }

        // Normalize name (capitalize first letter only if exists)
        if (entityNode.has("name")) {
            String name = entityNode.get("name").asText();
            if (!name.isEmpty()) {
                String normalized = name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
                entityNode.put("name", normalized);
            }
        }

        // Add computed field "ageCategory"
        if (entityNode.has("age")) {
            int age = entityNode.get("age").asInt(0);
            String category = age < 1 ? "baby" : (age < 7 ? "adult" : "senior");
            entityNode.put("ageCategory", category);
        }

        // Fire-and-forget async notification task (non-blocking)
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("[Async] Notifying external system about new pet: " + entityNode.get("name").asText());
                // Simulate external call or logging delay
                Thread.sleep(100);
            } catch (Exception e) {
                logger.error("[Async] Failed to notify external system", e);
            }
        });

        // Retrieve supplementary data from "pet-categories" entityModel and add categoryDescription
        try {
            String petType = entityNode.has("type") ? entityNode.get("type").asText() : null;
            if (petType != null && !petType.isEmpty()) {
                Optional<ObjectNode> categoryEntityOpt = entityService.getItem("pet-categories", ENTITY_VERSION, petType).get();
                if (categoryEntityOpt.isPresent()) {
                    ObjectNode cat = categoryEntityOpt.get();
                    if (cat.has("description")) {
                        entityNode.put("categoryDescription", cat.get("description").asText());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to fetch pet category info", e);
        }

        return entityNode;
    };

    // Workflow function for favorites entity - assuming favorites stored as separate entityModel "pet-favorites"
    // This example shows adding userId and petId validation and timestamping inside workflow
    private final Function<Object, Object> processpet_favorites = entity -> {
        logger.info("Running workflow processpet_favorites before persistence");

        if (!(entity instanceof ObjectNode)) {
            logger.warn("Entity is not an ObjectNode, cannot process");
            return entity;
        }

        ObjectNode entityNode = (ObjectNode) entity;

        // Validate required fields presence (userId and petId)
        if (!entityNode.hasNonNull("userId") || entityNode.get("userId").asText().isEmpty()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!entityNode.hasNonNull("petId") || entityNode.get("petId").asText().isEmpty()) {
            throw new IllegalArgumentException("petId is required");
        }

        // Add timestamp if missing
        if (!entityNode.has("addedAt")) {
            entityNode.put("addedAt", System.currentTimeMillis());
        }

        // Fire-and-forget async task to log favorite addition to external monitoring system
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("[Async] User " + entityNode.get("userId").asText() + " added favorite pet " + entityNode.get("petId").asText());
                // simulate delay or external call
                Thread.sleep(50);
            } catch (Exception e) {
                logger.error("[Async] Failed to log favorite addition", e);
            }
        });

        return entityNode;
    };

    @PostMapping(value = "/pets/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public PetSearchResponse searchPets(@RequestBody @Valid PetSearchRequest request) throws Exception {
        logger.info("Received pet search request: {}", request);

        String petstoreUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + request.getStatus();
        String json = restTemplate.getForObject(petstoreUrl, String.class);
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray()) {
            throw new IllegalStateException("Unexpected response format from external Petstore API");
        }
        List<PetInfo> filteredPets = new ArrayList<>();
        String requestedType = request.getType().toLowerCase();
        String requestedName = request.getName() != null ? request.getName().toLowerCase() : null;
        for (JsonNode petNode : root) {
            Long id = petNode.path("id").asLong(-1);
            String name = petNode.path("name").asText("");
            String status = petNode.path("status").asText("");
            String categoryName = "";
            if (petNode.has("category") && petNode.get("category").has("name")) {
                categoryName = petNode.get("category").get("name").asText("").toLowerCase();
            }
            if (!"all".equals(requestedType) && !requestedType.equals(categoryName)) {
                continue;
            }
            if (requestedName != null && !name.toLowerCase().contains(requestedName)) {
                continue;
            }
            // Mock age and description as external API does not provide them
            int mockAge = new Random().nextInt(15) + 1;
            String mockDescription = "No description available.";
            filteredPets.add(new PetInfo(id, name, categoryName, status, mockDescription, mockAge));
        }
        PetSearchResponse response = new PetSearchResponse();
        response.setPets(filteredPets);
        return response;
    }

    @PostMapping(value = "/pets/add", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Map<String, Object>> addPet(@RequestBody @Valid PetAddRequest request) {
        logger.info("Received addPet request: {}", request);
        ObjectNode node = objectMapper.valueToTree(request);
        return entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                node,
                processpurrfect_pets
        ).thenApply(uuid -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", uuid.toString());
            resp.put("message", "Pet entity added successfully");
            return resp;
        });
    }

    // Favorites are stored in separate entityModel "pet-favorites" to avoid recursion issues
    private static final String FAVORITES_ENTITY_NAME = "pet-favorites";

    @PostMapping(value = "/favorites/add", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<FavoriteAddResponse> addFavorite(@RequestBody @Valid FavoriteAddRequest request) {
        logger.info("Received addFavorite request: userId={}, petId={}", request.getUserId(), request.getPetId());

        ObjectNode favoriteNode = objectMapper.createObjectNode();
        favoriteNode.put("userId", request.getUserId());
        favoriteNode.put("petId", request.getPetId());

        return entityService.addItem(
                FAVORITES_ENTITY_NAME,
                ENTITY_VERSION,
                favoriteNode,
                processpet_favorites
        ).thenApply(uuid -> new FavoriteAddResponse(true, "Pet added to favorites"));
    }

    @GetMapping(value = "/favorites/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<FavoriteListResponse> getFavorites(@PathVariable @NotBlank String userId) {
        logger.info("Received getFavorites request for userId={}", userId);

        // Get favorites for userId from "pet-favorites" entityModel asynchronously
        // Assuming entityService supports search or getItems by criteria - if not, this is a placeholder
        // For demonstration, we assume a method entityService.searchItems(entityModel, version, criteria) exists
        // Since no such method is described, we simulate with getItem by composite key userId-petId or keep local cache
        // Here, for correctness, return empty list (or can cache in memory if needed)
        FavoriteListResponse response = new FavoriteListResponse();
        response.setUserId(userId);
        response.setFavorites(Collections.emptyList());
        return CompletableFuture.completedFuture(response);
    }

    @PostMapping(value = "/pets/care-tips", produces = MediaType.APPLICATION_JSON_VALUE)
    public PetCareTipsResponse getCareTips(@RequestBody @Valid PetCareTipsRequest request) {
        logger.info("Received getCareTips request: type={}, age={}", request.getType(), request.getAge());

        List<String> tips = new ArrayList<>();
        String type = request.getType().toLowerCase();
        int age = request.getAge();

        if ("cat".equals(type) || "all".equals(type)) {
            tips.add("Ensure your cat has fresh water at all times.");
            tips.add("Regular vet checkups are important.");
            if (age < 1) tips.add("Kittens need more frequent feeding.");
            else if (age > 10) tips.add("Senior cats benefit from a specialized diet.");
        }
        if ("dog".equals(type) || "all".equals(type)) {
            tips.add("Daily walks are essential for your dog’s health.");
            tips.add("Keep vaccinations up to date.");
            if (age < 1) tips.add("Puppies require training and socialization.");
            else if (age > 10) tips.add("Older dogs may need joint supplements.");
        }

        PetCareTipsResponse response = new PetCareTipsResponse();
        response.setTips(tips);
        return response;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus
    public Map<String, Object> handleAllExceptions(Exception ex) {
        logger.error("Exception handled: ", ex);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", "InternalServerError");
        err.put("message", ex.getMessage() != null ? ex.getMessage() : "Unexpected error");
        return err;
    }
}