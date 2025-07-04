package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> favoriteMap = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PETSTORE_BASE_URL = "https://petstore.swagger.io/v2";
    private long petIdSequence = 1000L;

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    @PostMapping("/search") // must be first annotation
    public ResponseEntity<SearchPetsResponse> searchPets(@RequestBody @Valid SearchPetsRequest request) {
        logger.info("Received searchPets request: type='{}', status='{}'", request.getType(), request.getStatus());
        try {
            if (!StringUtils.hasText(request.getStatus()) && !StringUtils.hasText(request.getType())) {
                logger.info("No search criteria provided, returning empty pet list");
                return ResponseEntity.ok(new SearchPetsResponse(Collections.emptyList()));
            }
            String url = StringUtils.hasText(request.getStatus())
                    ? PETSTORE_BASE_URL + "/pet/findByStatus?status=" + request.getStatus()
                    : PETSTORE_BASE_URL + "/pet/findByStatus?status=available";
            String rawResponse = restTemplate.getForObject(new URI(url), String.class);
            JsonNode rootNode = objectMapper.readTree(rawResponse);
            List<Pet> pets = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    Pet pet = mapPetstoreJsonToPet(petNode);
                    if (pet != null && (request.getType() == null || request.getType().equalsIgnoreCase(pet.getType()))) {
                        pets.add(pet);
                    }
                }
            } else {
                logger.error("Unexpected JSON response from Petstore API at /pet/findByStatus");
            }
            pets.forEach(p -> p.setFavorite(favoriteMap.getOrDefault(p.getId(), false)));
            logger.info("Search returned {} pets", pets.size());
            return ResponseEntity.ok(new SearchPetsResponse(pets));
        } catch (Exception e) {
            logger.error("Failed to search pets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to search pets", e);
        }
    }

    @GetMapping // must be first annotation
    public ResponseEntity<SearchPetsResponse> getPets(@Valid @ModelAttribute PetsQuery query) {
        // Using @ModelAttribute to bind GET query params
        logger.info("Received getPets request: type='{}', status='{}'", query.getType(), query.getStatus());
        try {
            List<Pet> filtered = new ArrayList<>();
            for (Pet pet : petStore.values()) {
                boolean matches = true;
                if (query.getType() != null) matches &= query.getType().equalsIgnoreCase(pet.getType());
                if (query.getStatus() != null) matches &= query.getStatus().equalsIgnoreCase(pet.getStatus());
                if (matches) {
                    pet.setFavorite(favoriteMap.getOrDefault(pet.getId(), false));
                    filtered.add(pet);
                }
            }
            logger.info("Returning {} pets from local store", filtered.size());
            return ResponseEntity.ok(new SearchPetsResponse(filtered));
        } catch (Exception e) {
            logger.error("Failed to get pets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to get pets", e);
        }
    }

    @PostMapping // must be first annotation
    public ResponseEntity<CreatePetResponse> addPet(@RequestBody @Valid CreatePetRequest request) {
        logger.info("Received addPet request: name='{}', type='{}', status='{}'", request.getName(), request.getType(), request.getStatus());
        long newId = generatePetId();
        Pet pet = new Pet(newId, request.getName(), request.getType(), request.getStatus(),
                request.getPhotoUrls(), false);
        petStore.put(newId, pet);
        logger.info("Pet created with id={}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatePetResponse(newId, "Pet created successfully"));
    }

    @PostMapping("/{id}/favorite") // must be first annotation
    public ResponseEntity<FavoriteResponse> markFavorite(@PathVariable("id") @NotNull Long id,
                                                         @RequestBody @Valid FavoriteRequest request) {
        logger.info("Received markFavorite request for petId={} favorite={}", id, request.getFavorite());
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        favoriteMap.put(id, request.getFavorite());
        pet.setFavorite(request.getFavorite());
        logger.info("Favorite status updated for petId={} to {}", id, request.getFavorite());
        return ResponseEntity.ok(new FavoriteResponse(id, request.getFavorite(), "Favorite status updated"));
    }

    @GetMapping("/{id}") // must be first annotation
    public ResponseEntity<Pet> getPetById(@PathVariable("id") @NotNull Long id) {
        logger.info("Received getPetById request for id={}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        pet.setFavorite(favoriteMap.getOrDefault(id, false));
        return ResponseEntity.ok(pet);
    }

    private Pet mapPetstoreJsonToPet(JsonNode petNode) {
        try {
            Long id = petNode.has("id") && !petNode.get("id").isNull() ? petNode.get("id").asLong() : null;
            String name = petNode.has("name") && !petNode.get("name").isNull() ? petNode.get("name").asText() : null;
            String status = petNode.has("status") && !petNode.get("status").isNull() ? petNode.get("status").asText() : null;
            String type = null;
            if (petNode.has("category") && petNode.get("category").has("name") && !petNode.get("category").get("name").isNull()) {
                type = petNode.get("category").get("name").asText();
            }
            List<String> photoUrls = new ArrayList<>();
            if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
                for (JsonNode urlNode : petNode.get("photoUrls")) {
                    photoUrls.add(urlNode.asText());
                }
            }
            if (id == null || name == null) return null;
            return new Pet(id, name, type, status, photoUrls, false);
        } catch (Exception e) {
            logger.error("Failed to map Petstore JSON to Pet", e);
            return null;
        }
    }

    private synchronized long generatePetId() {
        return petIdSequence++;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

    @Data
    public static class PetsQuery {
        @Size(max = 50)
        private String type;
        @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    public static class SearchPetsRequest {
        @Size(max = 50)
        private String type;
        @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class SearchPetsResponse {
        private List<Pet> pets;
    }

    @Data
    public static class CreatePetRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 100)
        private String type;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> photoUrls;
    }

    @Data
    public static class FavoriteRequest {
        @NotNull
        private Boolean favorite;
    }

    @Data
    @AllArgsConstructor
    public static class FavoriteResponse {
        private Long id;
        private Boolean favorite;
        private String message;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
        private Boolean favorite;
    }
}