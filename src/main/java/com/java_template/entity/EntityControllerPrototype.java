package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> userFavorites = new ConcurrentHashMap<>();
    private long petIdSequence = 1000L;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;
        private String name;
        private String category;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    static class SearchRequest {
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(max = 30)
        private String category;
        @Size(max = 50)
        private String nameContains;
    }

    @Data
    static class AddPetRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 30)
        private String category;
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> photoUrls;
    }

    @Data
    static class FavoriteRequest {
        @NotNull
        @Positive
        private Long userId;
    }

    @Data
    @AllArgsConstructor
    static class MessageResponse {
        private String message;
    }

    // POST /pets/search
    @PostMapping("/search")
    public ResponseEntity<Map<String, List<Pet>>> searchPets(@RequestBody @Valid SearchRequest searchRequest) {
        logger.info("Received search request: {}", searchRequest);
        try {
            String statusParam = Optional.ofNullable(searchRequest.getStatus()).orElse("available");
            URI uri = URI.create("https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam);
            HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.error("External API error: {}", response.statusCode());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "External API error");
            }
            JsonNode rootNode = objectMapper.readTree(response.body());
            List<Pet> filteredPets = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    Pet pet = parsePetFromJson(petNode);
                    if (searchRequest.getCategory() != null && !searchRequest.getCategory().equalsIgnoreCase(pet.getCategory())) continue;
                    if (searchRequest.getNameContains() != null &&
                        (pet.getName() == null || !pet.getName().toLowerCase().contains(searchRequest.getNameContains().toLowerCase())))
                        continue;
                    filteredPets.add(pet);
                }
            }
            Map<String, List<Pet>> result = Collections.singletonMap("pets", filteredPets);
            logger.info("Returning {} pets", filteredPets.size());
            return ResponseEntity.ok(result);
        } catch (IOException | InterruptedException e) {
            logger.error("Error searching pets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error processing request");
        }
    }

    // POST /pets
    @PostMapping
    public ResponseEntity<Map<String, Object>> addPet(@RequestBody @Valid AddPetRequest addPetRequest) {
        logger.info("Adding new pet: {}", addPetRequest);
        Pet newPet = new Pet();
        synchronized (this) { newPet.setId(++petIdSequence); }
        newPet.setName(addPetRequest.getName());
        newPet.setCategory(addPetRequest.getCategory());
        newPet.setStatus(Optional.ofNullable(addPetRequest.getStatus()).orElse("available"));
        newPet.setPhotoUrls(addPetRequest.getPhotoUrls());
        petStore.put(newPet.getId(), newPet);
        logger.info("Pet added with ID {}", newPet.getId());
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", newPet.getId());
        resp.put("message", "Pet added successfully");
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    // GET /pets/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @Positive Long id) {
        logger.info("Fetching pet ID {}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            logger.error("Pet not found {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // POST /pets/{id}/favorite
    @PostMapping("/{id}/favorite")
    public ResponseEntity<MessageResponse> markFavorite(
            @PathVariable @Positive Long id,
            @RequestBody @Valid FavoriteRequest favoriteRequest) {
        logger.info("Marking favorite pet {} for user {}", id, favoriteRequest.getUserId());
        if (!petStore.containsKey(id)) {
            logger.error("Pet not found {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        userFavorites.computeIfAbsent(favoriteRequest.getUserId(), k -> ConcurrentHashMap.newKeySet()).add(id);
        return ResponseEntity.ok(new MessageResponse("Pet marked as favorite"));
    }

    private Pet parsePetFromJson(JsonNode petNode) {
        Pet pet = new Pet();
        pet.setId(petNode.path("id").asLong());
        pet.setName(petNode.path("name").asText(null));
        JsonNode categoryNode = petNode.path("category");
        pet.setCategory(categoryNode.isObject() ? categoryNode.path("name").asText(null) : null);
        pet.setStatus(petNode.path("status").asText(null));
        List<String> photos = new ArrayList<>();
        JsonNode photosNode = petNode.path("photoUrls");
        if (photosNode.isArray()) {
            for (JsonNode photoUrlNode : photosNode) {
                photos.add(photoUrlNode.asText());
            }
        }
        pet.setPhotoUrls(photos);
        return pet;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason());
        Map<String, String> error = Collections.singletonMap("error", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Handling generic exception", ex);
        Map<String, String> error = Collections.singletonMap("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}