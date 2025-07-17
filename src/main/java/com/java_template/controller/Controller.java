package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_NAME;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@Validated
@RequestMapping(path = "/cyoda/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @PostMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AddUpdatePetResponse> addOrUpdatePet(@RequestBody @Valid Pet pet) throws ExecutionException, InterruptedException {
        logger.info("Received add/update for pet with technicalId={}", pet.getTechnicalId());
        if (pet.getTechnicalId() == null) {
            UUID newId = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet).get();
            pet.setTechnicalId(newId);
            logger.info("Generated new pet technicalId={}", newId);
        } else {
            entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, pet.getTechnicalId(), pet).get();
        }
        // Business logic moved to processors - no enrichment here
        return ResponseEntity.ok(new AddUpdatePetResponse(true, pet));
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Retrieving pet technicalId={}", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, UUID.fromString(id));
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.convertValue(item, Pet.class);
        if (item.hasNonNull("technicalId")) {
            pet.setTechnicalId(UUID.fromString(item.get("technicalId").asText()));
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid PetSearchRequest req) throws ExecutionException, InterruptedException {
        logger.info("Searching pets name={}, status={}, category={}", req.getName(), req.getStatus(), req.getCategory());

        List<Condition> conditions = new ArrayList<>();
        if (req.getName() != null && !req.getName().isBlank()) {
            conditions.add(Condition.of("$.name", "IEQUALS", req.getName()));
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            conditions.add(Condition.of("$.status", "IEQUALS", req.getStatus()));
        }
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            conditions.add(Condition.of("$.category", "IEQUALS", req.getCategory()));
        }

        List<Pet> results = new ArrayList<>();
        if (!conditions.isEmpty()) {
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
            ArrayNode items = filteredItemsFuture.get();
            if (items != null) {
                for (JsonNode node : items) {
                    Pet pet = objectMapper.convertValue(node, Pet.class);
                    if (node.hasNonNull("technicalId"))
                        pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                    results.add(pet);
                }
            }
        } else {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
            ArrayNode items = itemsFuture.get();
            if (items != null) {
                for (JsonNode node : items) {
                    Pet pet = objectMapper.convertValue(node, Pet.class);
                    if (node.hasNonNull("technicalId"))
                        pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                    results.add(pet);
                }
            }
        }

        // External API search fallback removed - moved to processors

        return ResponseEntity.ok(results);
    }

    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeletePetResponse> deletePet(@RequestBody @Valid DeletePetRequest req) throws ExecutionException, InterruptedException {
        logger.info("Deleting pet technicalId={}", req.getId());
        UUID id = UUID.fromString(req.getId());
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
        if (deletedItemId.get() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(new DeletePetResponse(true, "Pet deleted successfully"));
    }

    @Data
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;

        private String id; // legacy or external id

        @NotBlank
        private String name;

        @jakarta.validation.constraints.Size(min = 1, max = 50)
        private String category;

        @jakarta.validation.constraints.Size(min = 1, max = 20)
        private String status;

        @jakarta.validation.constraints.Size(max = 10)
        private List<@NotBlank String> tags = new ArrayList<>();

        @jakarta.validation.constraints.Size(max = 10)
        private List<@NotBlank String> photoUrls = new ArrayList<>();
    }

    @Data
    public static class AddUpdatePetResponse {
        private final boolean success;
        private final Pet pet;
    }

    @Data
    public static class PetSearchRequest {
        @jakarta.validation.constraints.Size(max = 50)
        private String name;
        @jakarta.validation.constraints.Size(max = 20)
        private String status;
        @jakarta.validation.constraints.Size(max = 50)
        private String category;
    }

    @Data
    public static class DeletePetRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class DeletePetResponse {
        private final boolean success;
        private final String message;
    }
}
