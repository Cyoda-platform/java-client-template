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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final EntityService entityService;

    private static final String ENTITY_NAME = "Pet";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private UUID technicalId;
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

    private final Map<Long, Set<UUID>> userFavorites = new HashMap<>();

    @PostMapping("/search")
    public ResponseEntity<Map<String, List<Pet>>> searchPets(@RequestBody @Valid SearchRequest searchRequest) throws IOException, InterruptedException {
        logger.info("Received search request: {}", searchRequest);
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
                if (searchRequest.getCategory() != null && !searchRequest.getCategory().equalsIgnoreCase(pet.getCategory()))
                    continue;
                if (searchRequest.getNameContains() != null &&
                        (pet.getName() == null || !pet.getName().toLowerCase(Locale.ROOT).contains(searchRequest.getNameContains().toLowerCase(Locale.ROOT))))
                    continue;
                filteredPets.add(pet);
            }
        }
        Map<String, List<Pet>> result = Collections.singletonMap("pets", filteredPets);
        logger.info("Returning {} pets", filteredPets.size());
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addPet(@RequestBody @Valid AddPetRequest addPetRequest) throws ExecutionException, InterruptedException {
        ObjectNode petEntity = objectMapper.createObjectNode();
        petEntity.put("name", addPetRequest.getName());
        petEntity.put("category", addPetRequest.getCategory());
        petEntity.put("status", Optional.ofNullable(addPetRequest.getStatus()).orElse("available"));
        petEntity.putArray("photoUrls").addAll(objectMapper.valueToTree(addPetRequest.getPhotoUrls()));

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                petEntity
        );

        UUID technicalId = idFuture.get();
        petEntity.put("technicalId", technicalId.toString());

        Map<String, Object> resp = new HashMap<>();
        resp.put("technicalId", technicalId);
        resp.put("message", "Pet added successfully");

        logger.info("Pet added with technicalId {}", technicalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        logger.info("Fetching pet technicalId {}", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                id
        );
        ObjectNode itemNode = itemFuture.get();
        if (itemNode == null || itemNode.isEmpty()) {
            logger.error("Pet not found {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        Pet pet = objectMapper.convertValue(itemNode, Pet.class);
        if (itemNode.has("technicalId")) {
            try {
                pet.setTechnicalId(UUID.fromString(itemNode.get("technicalId").asText()));
            } catch (IllegalArgumentException ignored) {
                pet.setTechnicalId(null);
            }
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<MessageResponse> markFavorite(
            @PathVariable UUID id,
            @RequestBody @Valid FavoriteRequest favoriteRequest) throws ExecutionException, InterruptedException {

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                id
        );
        ObjectNode itemNode = itemFuture.get();
        if (itemNode == null || itemNode.isEmpty()) {
            logger.error("Pet not found {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        // Mark favorite in local cache (synchronous and local)
        userFavorites.computeIfAbsent(favoriteRequest.getUserId(), k -> new HashSet<>()).add(id);

        logger.info("Marked pet {} as favorite for user {}", id, favoriteRequest.getUserId());

        return ResponseEntity.ok(new MessageResponse("Pet marked as favorite"));
    }

    private Pet parsePetFromJson(JsonNode petNode) {
        Pet pet = new Pet();
        if (petNode.has("id")) {
            pet.setTechnicalId(UUID.nameUUIDFromBytes(Long.toString(petNode.get("id").asLong()).getBytes()));
        }
        pet.setName(petNode.path("name").asText(null));
        JsonNode categoryNode = petNode.path("category");
        if (categoryNode.isObject()) {
            pet.setCategory(categoryNode.path("name").asText(null));
        }
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
}