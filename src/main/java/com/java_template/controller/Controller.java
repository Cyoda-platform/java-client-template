package com.java_template.controller;

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

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping("cyoda/purrfect-pets")
public class Controller {

    private final Logger logger = LoggerFactory.getLogger(Controller.class);
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
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

    @PostMapping(value = "/pets/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public PetSearchResponse searchPets(@RequestBody @Valid PetSearchRequest request) throws Exception {
        logger.info("Received pet search request: {}", request);

        String petstoreUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + request.getStatus();
        String json = restTemplate.getForObject(petstoreUrl, String.class);
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray()) {
            logger.error("Unexpected response format from external Petstore API");
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
        logger.info("Returning {} pets for search request", filteredPets.size());
        return response;
    }

    @PostMapping(value = "/pets/add", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<Map<String, Object>> addPet(@RequestBody @Valid PetAddRequest request) {
        logger.info("Received addPet request: {}", request);
        ObjectNode node = objectMapper.valueToTree(request);
        return entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                node
        ).thenApply(uuid -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("id", uuid.toString());
            resp.put("message", "Pet entity added successfully");
            logger.info("Pet entity added with id {}", uuid.toString());
            return resp;
        }).exceptionally(ex -> {
            logger.error("Failed to add pet entity", ex);
            throw new RuntimeException(ex);
        });
    }

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
                favoriteNode
        ).thenApply(uuid -> {
            logger.info("Favorite added with id {}", uuid.toString());
            return new FavoriteAddResponse(true, "Pet added to favorites");
        }).exceptionally(ex -> {
            logger.error("Failed to add favorite", ex);
            return new FavoriteAddResponse(false, "Failed to add pet to favorites");
        });
    }

    @GetMapping(value = "/favorites/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CompletableFuture<FavoriteListResponse> getFavorites(@PathVariable @NotBlank String userId) {
        logger.info("Received getFavorites request for userId={}", userId);

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
            tips.add("Daily walks are essential for your dog's health.");
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