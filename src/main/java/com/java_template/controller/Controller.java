package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("cyoda/pets")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    private final EntityService entityService;

    private static final String ENTITY_NAME = "pet";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SearchResponse> searchPets(@RequestBody @Valid SearchRequest request) throws ExecutionException, InterruptedException {
        logger.info("searchPets request: {}", request);

        try {
            // Prepare minimal pet entities only with search parameters; enrichment and fetching in workflow
            List<ObjectNode> petsToAdd = new ArrayList<>();

            ObjectNode petNode = objectMapper.createObjectNode();
            petNode.put("status", request.getStatus());
            if (!"all".equalsIgnoreCase(request.getType())) {
                petNode.put("type", request.getType());
            }
            if (StringUtils.hasText(request.getName())) {
                petNode.put("name", request.getName());
            }
            petsToAdd.add(petNode);

            // Add items without workflow processing
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    petsToAdd
            );

            List<UUID> technicalIds = idsFuture.get();

            // Retrieve added pets by technicalId
            List<Pet> results = new ArrayList<>();
            for (UUID id : technicalIds) {
                ObjectNode persistedNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id).get();
                if (persistedNode != null && !persistedNode.isEmpty()) {
                    Pet pet = objectMapper.convertValue(persistedNode, Pet.class);
                    // Additional filtering on controller side (e.g. name contains)
                    if (StringUtils.hasText(request.getName())) {
                        if (pet.getName() == null || !pet.getName().toLowerCase().contains(request.getName().toLowerCase())) {
                            continue;
                        }
                    }
                    results.add(pet);
                }
            }

            return ResponseEntity.ok(new SearchResponse(results));
        } catch (Exception e) {
            logger.error("searchPets error", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @PostMapping(path = "/match", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MatchResponse> matchPets(@RequestBody @Valid MatchRequest request) throws ExecutionException, InterruptedException {
        logger.info("matchPets request: {}", request);

        try {
            // Prepare minimal pet entity with type and status=available; enrichment done in workflow
            ObjectNode petNode = objectMapper.createObjectNode();
            petNode.put("type", request.getType());
            petNode.put("status", "available");

            List<ObjectNode> petsToAdd = Collections.singletonList(petNode);

            CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    petsToAdd
            );

            List<UUID> technicalIds = idsFuture.get();

            List<Pet> matches = new ArrayList<>();
            for (UUID id : technicalIds) {
                ObjectNode persistedNode = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id).get();
                if (persistedNode != null && !persistedNode.isEmpty()) {
                    Pet pet = objectMapper.convertValue(persistedNode, Pet.class);
                    // Apply filtering on age and friendly in controller to avoid recursion in workflow
                    if (pet.getAge() != null && pet.getAge() >= request.getAgeMin() && pet.getAge() <= request.getAgeMax()
                            && pet.getFriendly() != null && pet.getFriendly() == request.isFriendly()) {
                        matches.add(pet);
                    }
                }
            }

            return ResponseEntity.ok(new MatchResponse(matches));
        } catch (Exception e) {
            logger.error("matchPets error", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        }
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pet> getPetById(@PathVariable("id") UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.convertValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @GetMapping(path = "/favorites", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FavoritesResponse> getFavorites() throws ExecutionException, InterruptedException {
        // Retrieve all pets and filter favorites by status=="favorite"
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();

        List<PetSummary> list = new ArrayList<>();
        for (JsonNode node : items) {
            String status = node.path("status").asText("");
            if ("favorite".equalsIgnoreCase(status)) {
                UUID techId = null;
                try {
                    techId = UUID.fromString(node.path("technicalId").asText());
                } catch (Exception ignored) {}
                PetSummary ps = new PetSummary(
                        techId != null ? techId.getMostSignificantBits() : 0L,
                        node.path("name").asText(null),
                        node.path("type").asText(null)
                );
                list.add(ps);
            }
        }
        return ResponseEntity.ok(new FavoritesResponse(list));
    }

    @PostMapping(path = "/favorites", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ActionResponse> modifyFavorites(@RequestBody @Valid FavoriteRequest request) throws ExecutionException, InterruptedException {
        UUID technicalId = convertLongToUUID(request.getPetId());
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.convertValue(node, Pet.class);

        if ("add".equalsIgnoreCase(request.getAction())) {
            pet.setStatus("favorite");
            CompletableFuture<UUID> updatedId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, pet);
            updatedId.get();
            return ResponseEntity.ok(new ActionResponse("success", "Pet added"));
        }
        if ("remove".equalsIgnoreCase(request.getAction())) {
            pet.setStatus("available");
            CompletableFuture<UUID> updatedId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, pet);
            updatedId.get();
            return ResponseEntity.ok(new ActionResponse("success", "Pet removed"));
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action");
    }

    private UUID convertLongToUUID(Long id) {
        return new UUID(id != null ? id : 0L, 0L);
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
        private UUID technicalId;
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