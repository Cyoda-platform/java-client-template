package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petStorage = new ConcurrentHashMap<>();
    private final Map<Long, Pet> favorites = new ConcurrentHashMap<>();

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResponse> searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("searchPets request: {}", request);
        try {
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + request.getStatus();
            JsonNode responseJson = restTemplate.getForObject(url, JsonNode.class);
            if (responseJson == null || !responseJson.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }
            List<Pet> results = new ArrayList<>();
            for (JsonNode node : responseJson) {
                long id = node.path("id").asLong();
                String name = node.path("name").asText(null);
                String status = node.path("status").asText(null);
                String type = extractCategoryName(node.path("category"));
                String description = node.path("description").asText(null);
                if (!"all".equalsIgnoreCase(request.getType()) && !request.getType().equalsIgnoreCase(type)) {
                    continue;
                }
                if (StringUtils.hasText(request.getName())
                        && (name == null || !name.toLowerCase().contains(request.getName().toLowerCase()))) {
                    continue;
                }
                Pet pet = new Pet(id, name, type, status, description, null, null);
                petStorage.put(id, pet);
                results.add(pet);
            }
            return ResponseEntity.ok(new SearchResponse(results));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("searchPets error", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @PostMapping(path = "/match", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MatchResponse> matchPets(@RequestBody @Valid MatchRequest request) {
        logger.info("matchPets request: {}", request);
        try {
            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
            JsonNode responseJson = restTemplate.getForObject(url, JsonNode.class);
            if (responseJson == null || !responseJson.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }
            List<Pet> matches = new ArrayList<>();
            for (JsonNode node : responseJson) {
                long id = node.path("id").asLong();
                String name = node.path("name").asText(null);
                String status = node.path("status").asText(null);
                String type = extractCategoryName(node.path("category"));
                Integer age = node.path("age").isInt() ? node.path("age").asInt() : new Random().nextInt(10) + 1;
                boolean friendly = new Random().nextBoolean();
                Pet pet = new Pet(id, name, type, status, null, age, friendly);
                petStorage.put(id, pet);
                if (!request.getType().equalsIgnoreCase(type)) continue;
                if (age < request.getAgeMin() || age > request.getAgeMax()) continue;
                if (request.isFriendly() != friendly) continue;
                matches.add(pet);
            }
            return ResponseEntity.ok(new MatchResponse(matches));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("matchPets error", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pet> getPetById(@PathVariable("id") Long id) {
        Pet pet = petStorage.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @GetMapping(path = "/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FavoritesResponse> getFavorites() {
        List<PetSummary> list = new ArrayList<>();
        favorites.values().forEach(p -> list.add(new PetSummary(p.getId(), p.getName(), p.getType())));
        return ResponseEntity.ok(new FavoritesResponse(list));
    }

    @PostMapping(path = "/favorites", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ActionResponse> modifyFavorites(@RequestBody @Valid FavoriteRequest request) {
        Pet pet = petStorage.get(request.getPetId());
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if ("add".equalsIgnoreCase(request.getAction())) {
            favorites.put(pet.getId(), pet);
            return ResponseEntity.ok(new ActionResponse("success", "Pet added"));
        }
        if ("remove".equalsIgnoreCase(request.getAction())) {
            favorites.remove(pet.getId());
            return ResponseEntity.ok(new ActionResponse("success", "Pet removed"));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action");
    }

    private String extractCategoryName(JsonNode categoryNode) {
        if (categoryNode != null && categoryNode.has("name")) {
            return categoryNode.get("name").asText();
        }
        return null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {
        @NotBlank
        @Pattern(regexp = "cat|dog|all")
        private String type;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private List<Pet> results;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchRequest {
        @NotBlank
        @Pattern(regexp = "cat|dog")
        private String type;
        @Min(0)
        private int ageMin;
        @Min(0)
        private int ageMax;
        private boolean friendly;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchResponse {
        private List<Pet> matches;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String description;
        private Integer age;
        private Boolean friendly;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoritesResponse {
        private List<PetSummary> favorites;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetSummary {
        private Long id;
        private String name;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FavoriteRequest {
        @NotNull
        private Long petId;
        @NotBlank
        @Pattern(regexp = "add|remove")
        private String action;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionResponse {
        private String status;
        private String message;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }
}