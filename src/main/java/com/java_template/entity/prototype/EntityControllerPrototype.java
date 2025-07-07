package com.java_template.entity.prototype;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping(path = "/prototype/purrfect-pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petCache = new ConcurrentHashMap<>();

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
        private List<String> tags;
    }

    @Data
    public static class SearchRequest {
        @NotNull
        @Pattern(regexp = "cat|dog|bird|all", message = "type must be cat, dog, bird, or all")
        private String type;

        @NotNull
        @Pattern(regexp = "available|pending|sold", message = "status must be available, pending, or sold")
        private String status;

        @Size(max = 100, message = "name must be at most 100 characters")
        private String name;
    }

    @Data
    public static class SearchResponse {
        private List<Pet> pets;
    }

    @Data
    public static class RecommendationsRequest {
        @NotBlank
        @Pattern(regexp = "cat|dog|bird|all", message = "preferredType must be cat, dog, bird, or all")
        private String preferredType;

        @NotBlank
        @Pattern(regexp = "available|pending|sold", message = "preferredStatus must be available, pending, or sold")
        private String preferredStatus;
    }

    @Data
    public static class RecommendationsResponse {
        private List<Pet> recommendedPets;
    }

    @PostMapping(path = "/pets/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("searchPets request: type={}, status={}, name={}", request.getType(), request.getStatus(), request.getName());
        try {
            String url = PETSTORE_BASE_URL + "/pet/findByStatus?status=" + request.getStatus();
            String rawJson = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawJson);
            List<Pet> filteredPets = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    Pet pet = parsePetFromJsonNode(petNode);
                    if (pet == null) continue;
                    if (!"all".equalsIgnoreCase(request.getType()) && !request.getType().equalsIgnoreCase(pet.getType())) {
                        continue;
                    }
                    if (request.getName() != null && !request.getName().isBlank() &&
                        !pet.getName().toLowerCase().contains(request.getName().toLowerCase())) {
                        continue;
                    }
                    filteredPets.add(pet);
                    petCache.put(pet.getId(), pet);
                }
            }
            logger.info("found {} pets", filteredPets.size());
            SearchResponse response = new SearchResponse();
            response.setPets(filteredPets);
            return response;
        } catch (Exception e) {
            logger.error("searchPets error", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets");
        }
    }

    @GetMapping(path = "/pets/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable @NotNull Long id) {
        logger.info("getPetById id={}", id);
        Pet pet = petCache.get(id);
        if (pet == null) {
            logger.warn("pet not found id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @PostMapping(path = "/pets/recommendations", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public RecommendationsResponse getRecommendations(@RequestBody @Valid RecommendationsRequest request) {
        logger.info("getRecommendations preferredType={}, preferredStatus={}", request.getPreferredType(), request.getPreferredStatus());
        try {
            String url = PETSTORE_BASE_URL + "/pet/findByStatus?status=" + request.getPreferredStatus();
            String rawJson = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(rawJson);
            List<Pet> recommended = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    Pet pet = parsePetFromJsonNode(petNode);
                    if (pet == null) continue;
                    if ("all".equalsIgnoreCase(request.getPreferredType()) ||
                        request.getPreferredType().equalsIgnoreCase(pet.getType())) {
                        recommended.add(pet);
                        petCache.put(pet.getId(), pet);
                    }
                }
            }
            RecommendationsResponse response = new RecommendationsResponse();
            response.setRecommendedPets(recommended);
            return response;
        } catch (Exception e) {
            logger.error("getRecommendations error", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get recommendations");
        }
    }

    private Pet parsePetFromJsonNode(JsonNode node) {
        try {
            Pet pet = new Pet();
            pet.setId(node.path("id").asLong());
            pet.setName(node.path("name").asText(null));
            pet.setStatus(node.path("status").asText(null));
            JsonNode category = node.path("category");
            pet.setType(category.isObject() ? category.path("name").asText(null) : null);
            List<String> photos = new ArrayList<>();
            node.path("photoUrls").forEach(p -> photos.add(p.asText()));
            pet.setPhotoUrls(photos);
            List<String> tags = new ArrayList<>();
            node.path("tags").forEach(t -> {
                String tag = t.path("name").asText(null);
                if (tag != null) tags.add(tag);
            });
            pet.setTags(tags);
            return pet;
        } catch (Exception e) {
            logger.error("parsePetFromJsonNode error", e);
            return null;
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, Object> error = new HashMap<>();
        error.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        error.put("message", "Internal server error");
        return error;
    }
}