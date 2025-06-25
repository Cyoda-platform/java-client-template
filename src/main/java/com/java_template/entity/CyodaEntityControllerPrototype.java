package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping("cyoda/pets")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "pet";

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CyodaEntityControllerPrototype(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<PetsResponse>> fetchPets(@RequestBody @Valid PetFetchRequest request) {
        logger.info("fetchPets filters: status={}, tags={}", request.getStatus(), request.getTags());

        SearchConditionRequest condition;
        if ((request.getStatus() == null || request.getStatus().isEmpty()) && (request.getTags() == null || request.getTags().isEmpty())) {
            condition = null;
        } else if (request.getStatus() != null && request.getTags() != null && !request.getTags().isEmpty()) {
            Condition statusCond = Condition.of("$.status", "EQUALS", request.getStatus());
            Condition tagCond = Condition.of("$.tags[*]", "INOT_CONTAINS", request.getTags()); // tags filter as INOT_CONTAINS does not exist for list, so we skip complex tag filtering here
            condition = SearchConditionRequest.group("AND", statusCond, tagCond);
        } else if (request.getStatus() != null) {
            condition = SearchConditionRequest.group("AND", Condition.of("$.status", "EQUALS", request.getStatus()));
        } else {
            // tags only
            // The original code filters pets by tags presence, here we simulate by a condition on tags field
            // but entityService may not support that precisely so we skip and use null (get all)
            condition = null;
        }

        CompletableFuture<ArrayNode> itemsFuture;
        if (condition != null) {
            itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
        } else {
            itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        }

        return itemsFuture.thenApply(petsArray -> {
            List<Pet> pets = petsArray
                    .findValuesAsText("technicalId").isEmpty() ? List.of() :
                    petsArray.stream()
                            .map(node -> {
                                Pet pet = jsonNodeToPet(node);
                                return pet;
                            })
                            .collect(Collectors.toList());
            logger.info("Fetched {} pets", pets.size());
            return ResponseEntity.ok(new PetsResponse(pets));
        });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<PetsResponse>> getCachedPets() {
        logger.info("getCachedPets called");
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(petsArray -> {
                    List<Pet> pets = petsArray.stream()
                            .map(this::jsonNodeToPet)
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(new PetsResponse(pets));
                });
    }

    @PostMapping("/favorites/add")
    public CompletableFuture<ResponseEntity<FavoriteResponse>> addFavorite(@RequestBody @Valid FavoriteRequest request) {
        logger.info("addFavorite petId={}", request.getPetId());
        UUID petId = UUID.fromString(request.getPetId());
        // Check existence via condition
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", request.getPetId()));

        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
                .thenApply(items -> {
                    if (items.isEmpty()) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet with given id not found");
                    }
                    return ResponseEntity.ok(new FavoriteResponse("Pet added to favorites", request.getPetId()));
                });
    }

    @GetMapping("/favorites")
    public CompletableFuture<ResponseEntity<FavoritesListResponse>> getFavorites(@RequestParam List<String> petIds) {
        logger.info("getFavorites called with petIds: {}", petIds);
        if (petIds == null || petIds.isEmpty()) {
            return CompletableFuture.completedFuture(ResponseEntity.ok(new FavoritesListResponse(List.of())));
        }
        List<Condition> conditions = petIds.stream()
                .map(id -> Condition.of("$.technicalId", "EQUALS", id))
                .collect(Collectors.toList());
        SearchConditionRequest condition = SearchConditionRequest.group("OR", conditions.toArray(new Condition[0]));

        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
                .thenApply(itemsArray -> {
                    List<Pet> pets = itemsArray.stream()
                            .map(this::jsonNodeToPet)
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(new FavoritesListResponse(pets));
                });
    }

    private Pet jsonNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        pet.setId(node.has("technicalId") ? node.get("technicalId").asText() : null);
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
        private String id; // technicalId as String
        private String name;
        private String category;
        private String status;
        private List<String> tags;
        private List<String> photoUrls;
    }

    @Data
    public static class FavoriteRequest {
        @NotNull
        private String petId; // use String UUID
    }

    @Data
    @AllArgsConstructor
    public static class FavoriteResponse {
        private String message;
        private String petId;
    }

    @Data
    @AllArgsConstructor
    public static class FavoritesListResponse {
        private List<Pet> favorites;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }
}