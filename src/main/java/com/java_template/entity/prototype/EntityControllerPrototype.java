package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile JsonNode cachedPets = null;
    private final Map<Long, PetFavorite> favorites = new ConcurrentHashMap<>();

    @PostMapping("/fetch")
    public ResponseEntity<PetsResponse> fetchPets(@RequestBody @Valid PetFetchRequest request) {
        logger.info("fetchPets filters: status={}, tags={}", request.getStatus(), request.getTags());
        try {
            String url = PETSTORE_API_BASE + "/findByStatus?status=" +
                         (request.getStatus() != null ? request.getStatus() : "available");
            JsonNode responseNode = restTemplate.getForObject(url, JsonNode.class);
            if (responseNode == null || !responseNode.isArray()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Invalid response from external Petstore API");
            }
            List<JsonNode> filtered = filterByTags(responseNode, request.getTags());
            cachedPets = objectMapper.valueToTree(filtered);
            List<Pet> pets = filtered.stream().map(this::jsonNodeToPet).collect(Collectors.toList());
            logger.info("Fetched {} pets", pets.size());
            return ResponseEntity.ok(new PetsResponse(pets));
        } catch (ResponseStatusException ex) {
            logger.error("fetchPets error: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            logger.error("fetchPets unexpected error", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Failed to fetch pets from external API");
        }
    }

    @GetMapping
    public ResponseEntity<PetsResponse> getCachedPets() {
        logger.info("getCachedPets called");
        if (cachedPets == null) {
            return ResponseEntity.ok(new PetsResponse(List.of()));
        }
        try {
            List<Pet> pets = objectMapper.convertValue(cachedPets, List.class)
                    .stream()
                    .map(objectMapper::convertValue)
                    .map(node -> (JsonNode) node)
                    .map(this::jsonNodeToPet)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(new PetsResponse(pets));
        } catch (Exception e) {
            logger.error("getCachedPets error", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read cached pets");
        }
    }

    @PostMapping("/favorites/add")
    public ResponseEntity<FavoriteResponse> addFavorite(@RequestBody @Valid FavoriteRequest request) {
        logger.info("addFavorite petId={}", request.getPetId());
        if (cachedPets == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "No pets data available, fetch first");
        }
        boolean exists = false;
        for (JsonNode petNode : cachedPets) {
            if (petNode.has("id") && petNode.get("id").asLong() == request.getPetId()) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Pet with given id not found");
        }
        favorites.put(request.getPetId(), new PetFavorite(request.getPetId(), Instant.now()));
        logger.info("Pet {} added to favorites", request.getPetId());
        return ResponseEntity.ok(new FavoriteResponse("Pet added to favorites", request.getPetId()));
    }

    @GetMapping("/favorites")
    public ResponseEntity<FavoritesListResponse> getFavorites() {
        logger.info("getFavorites called");
        if (favorites.isEmpty()) {
            return ResponseEntity.ok(new FavoritesListResponse(List.of()));
        }
        if (cachedPets == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "No pets data available, fetch first");
        }
        List<Pet> favoritePets = favorites.keySet().stream()
                .map(this::findPetInCached)
                .filter(p -> p != null)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new FavoritesListResponse(favoritePets));
    }

    private Pet findPetInCached(Long petId) {
        for (JsonNode petNode : cachedPets) {
            if (petNode.has("id") && petNode.get("id").asLong() == petId) {
                return jsonNodeToPet(petNode);
            }
        }
        return null;
    }

    private Pet jsonNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        pet.setId(node.has("id") ? node.get("id").asLong() : null);
        pet.setName(node.has("name") ? node.get("name").asText() : null);
        pet.setStatus(node.has("status") ? node.get("status").asText() : null);
        if (node.has("category") && node.get("category").has("name")) {
            pet.setCategory(node.get("category").get("name").asText());
        }
        if (node.has("tags") && node.get("tags").isArray()) {
            List<String> tags = objectMapper.convertValue(node.get("tags"), List.class)
                    .stream().map(Object::toString).collect(Collectors.toList());
            pet.setTags(tags);
        }
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            List<String> photos = objectMapper.convertValue(node.get("photoUrls"), List.class)
                    .stream().map(Object::toString).collect(Collectors.toList());
            pet.setPhotoUrls(photos);
        }
        return pet;
    }

    private List<JsonNode> filterByTags(JsonNode petsArray, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return objectMapper.convertValue(petsArray, List.class);
        }
        return objectMapper.convertValue(petsArray, List.class).stream()
                .map(objectMapper::convertValue).map(node -> (JsonNode) node)
                .filter(petNode -> {
                    if (!petNode.has("tags")) return false;
                    for (JsonNode tagNode : petNode.get("tags")) {
                        if (tagNode.has("name") && tags.contains(tagNode.get("name").asText())) {
                            return true;
                        }
                    }
                    return false;
                }).collect(Collectors.toList());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse error = new ErrorResponse(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                "Internal server error");
        return new ResponseEntity<>(error, org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Data
    public static class PetFetchRequest {
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(min = 1)
        private List<String> tags;
    }

    @Data
    @AllArgsConstructor
    public static class PetsResponse {
        private List<Pet> pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String category;
        private String status;
        private List<String> tags;
        private List<String> photoUrls;
    }

    @Data
    public static class FavoriteRequest {
        @NotNull
        private Long petId;
    }

    @Data
    @AllArgsConstructor
    public static class FavoriteResponse {
        private String message;
        private Long petId;
    }

    @Data
    @AllArgsConstructor
    public static class FavoritesListResponse {
        private List<Pet> favorites;
    }

    @Data
    @AllArgsConstructor
    public static class PetFavorite {
        private Long petId;
        private Instant addedAt;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}