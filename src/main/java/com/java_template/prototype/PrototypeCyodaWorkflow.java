package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/entity/pets")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "pets";
    private static final String EXTERNAL_PETSTORE_BASE = "https://petstore.swagger.io/v2/pet";

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Workflow function to process Pet entity before persistence.
     * This function is asynchronous and modifies the entity ObjectNode directly.
     * It can also get/add supplementary entities of other models.
     * Cannot add/update/delete entities of the same model (pets).
     */
    private CompletableFuture<ObjectNode> processPets(ObjectNode petEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (petEntity.has("source") && "external".equalsIgnoreCase(petEntity.get("source").asText())
                        && petEntity.has("id") && petEntity.get("id").canConvertToLong()) {
                    long petId = petEntity.get("id").asLong();

                    String url = EXTERNAL_PETSTORE_BASE + "/" + petId;
                    logger.info("Workflow fetching external pet details for id {}", petId);
                    String json = restTemplate.getForObject(url, String.class);
                    if (json != null) {
                        JsonNode externalNode = objectMapper.readTree(json);

                        petEntity.put("name", externalNode.path("name").asText(petEntity.path("name").asText("")));
                        petEntity.put("status", externalNode.path("status").asText(petEntity.path("status").asText("")));
                        JsonNode cat = externalNode.path("category");
                        if (!cat.isMissingNode()) {
                            petEntity.put("type", cat.path("name").asText(petEntity.path("type").asText("unknown")));
                        }
                        petEntity.remove("source");
                    }
                } else {
                    if (!petEntity.has("age") || petEntity.get("age").isNull()) {
                        petEntity.put("age", 0);
                    }
                    petEntity.remove("source");
                }
            } catch (Exception ex) {
                logger.error("Error in processPets workflow", ex);
            }
            return petEntity;
        });
    }

    @PostMapping
    public ResponseEntity<PetResponse> addOrUpdatePet(@RequestBody @Valid AddOrUpdatePetRequest request) throws ExecutionException, InterruptedException {
        logger.info("addOrUpdatePet source={}", request.getSource());
        try {
            if ("external".equalsIgnoreCase(request.getSource())) {
                if (request.getPetId() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "petId required for external source");
                }
                long petId = request.getPetId();

                ObjectNode petEntity = objectMapper.createObjectNode();
                petEntity.put("id", petId);
                petEntity.put("source", "external");

                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.id", "EQUALS", petId));
                CompletableFuture<ArrayNode> existingItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
                ArrayNode existingItems = existingItemsFuture.get();

                if (existingItems.size() > 0) {
                    ObjectNode existingNode = (ObjectNode) existingItems.get(0);
                    UUID technicalId = UUID.fromString(existingNode.get("technicalId").asText());

                    CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, petEntity);
                    updatedIdFuture.get();

                    Pet updatedPet = mapNodeToPet(existingNode.deepCopy().setAll(petEntity));
                    return ResponseEntity.ok(new PetResponse(true, updatedPet));
                } else {
                    CompletableFuture<UUID> addedIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petEntity, this::processPets);
                    UUID technicalId = addedIdFuture.get();

                    SearchConditionRequest cond = SearchConditionRequest.group("AND",
                            Condition.of("$.technicalId", "EQUALS", technicalId.toString()));
                    ArrayNode savedEntities = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, cond).get();
                    if (savedEntities.size() > 0) {
                        Pet savedPet = mapNodeToPet(savedEntities.get(0));
                        return ResponseEntity.ok(new PetResponse(true, savedPet));
                    } else {
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve saved pet");
                    }
                }
            } else if ("internal".equalsIgnoreCase(request.getSource())) {

                ObjectNode petEntity = objectMapper.createObjectNode();

                if (request.getPetId() != null) {
                    petEntity.put("id", request.getPetId());
                }
                if (request.getName() != null) {
                    petEntity.put("name", request.getName());
                }
                if (request.getType() != null) {
                    petEntity.put("type", request.getType());
                }
                if (request.getAge() != null) {
                    petEntity.put("age", request.getAge());
                } else {
                    petEntity.put("age", 0);
                }
                if (request.getStatus() != null) {
                    petEntity.put("status", request.getStatus());
                }

                UUID technicalId = null;
                if (request.getPetId() != null) {
                    SearchConditionRequest condition = SearchConditionRequest.group("AND",
                            Condition.of("$.id", "EQUALS", request.getPetId()));
                    ArrayNode existingItems = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition).get();
                    if (existingItems.size() > 0) {
                        ObjectNode existingNode = (ObjectNode) existingItems.get(0);
                        technicalId = UUID.fromString(existingNode.get("technicalId").asText());

                        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, petEntity);
                        updatedIdFuture.get();

                        Pet updatedPet = mapNodeToPet(existingNode.deepCopy().setAll(petEntity));
                        logger.info("Stored internal pet id={}", request.getPetId());
                        return ResponseEntity.ok(new PetResponse(true, updatedPet));
                    }
                }

                CompletableFuture<UUID> addedIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petEntity, this::processPets);
                addedIdFuture.get();

                if (petEntity.has("id")) {
                    SearchConditionRequest cond = SearchConditionRequest.group("AND",
                            Condition.of("$.id", "EQUALS", petEntity.get("id").asLong()));
                    ArrayNode savedEntities = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, cond).get();
                    if (savedEntities.size() > 0) {
                        Pet savedPet = mapNodeToPet(savedEntities.get(0));
                        logger.info("Stored internal pet id={}", savedPet.getId());
                        return ResponseEntity.ok(new PetResponse(true, savedPet));
                    }
                }

                Pet pet = new Pet(
                        petEntity.has("id") ? petEntity.get("id").asLong() : 0,
                        petEntity.path("name").asText(""),
                        petEntity.path("type").asText(""),
                        petEntity.path("age").asInt(0),
                        petEntity.path("status").asText("")
                );
                return ResponseEntity.ok(new PetResponse(true, pet));
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid source");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("AddOrUpdatePet error", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        }
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody @Valid SearchRequest request) throws ExecutionException, InterruptedException {
        logger.info("searchPets type={},status={},minAge={},maxAge={}",
                request.getType(), request.getStatus(), request.getMinAge(), request.getMaxAge());

        List<Condition> conditions = new ArrayList<>();
        if (request.getType() != null) {
            conditions.add(Condition.of("$.type", "IEQUALS", request.getType()));
        }
        if (request.getStatus() != null) {
            conditions.add(Condition.of("$.status", "IEQUALS", request.getStatus()));
        }
        if (request.getMinAge() != null) {
            conditions.add(Condition.of("$.age", "GREATER_OR_EQUAL", request.getMinAge()));
        }
        if (request.getMaxAge() != null) {
            conditions.add(Condition.of("$.age", "LESS_OR_EQUAL", request.getMaxAge()));
        }

        SearchConditionRequest conditionRequest = conditions.isEmpty() ? null : SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
        CompletableFuture<ArrayNode> resultsFuture = conditionRequest == null
                ? entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                : entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode resultsNode = resultsFuture.get();

        List<Pet> results = new ArrayList<>();
        for (JsonNode node : resultsNode) {
            Pet pet = mapNodeToPet(node);
            results.add(pet);
        }
        return ResponseEntity.ok(new SearchResponse(results));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") @Min(1) long id) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition);
        ArrayNode items = itemsFuture.get();
        if (items.size() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = mapNodeToPet(items.get(0));
        return ResponseEntity.ok(pet);
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getAllPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        List<Pet> pets = new ArrayList<>();
        for (JsonNode node : items) {
            pets.add(mapNodeToPet(node));
        }
        return ResponseEntity.ok(pets);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleError(ResponseStatusException ex) {
        logger.error("error {} {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    private Pet mapNodeToPet(JsonNode node) {
        long id = node.path("id").asLong(0);
        String name = node.path("name").asText("");
        String type = node.path("type").asText("");
        int age = node.path("age").asInt(0);
        String status = node.path("status").asText("");
        return new Pet(id, name, type, age, status);
    }

    @Data
    public static class AddOrUpdatePetRequest {
        @NotBlank
        private String source; // external or internal
        private Long petId;
        @Size(min = 1)
        private String name;
        @Size(min = 1)
        private String type;
        @Min(0)
        private Integer age;
        @Size(min = 1)
        private String status;
    }

    @Data
    public static class SearchRequest {
        @Size(min = 1)
        private String type;
        @Size(min = 1)
        private String status;
        @Min(0)
        private Integer minAge;
        @Min(0)
        private Integer maxAge;
    }

    @Data
    public static class PetResponse {
        private final boolean success;
        private final Pet pet;
    }

    @Data
    public static class SearchResponse {
        private final List<Pet> results;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }

    @Data
    public static class Pet {
        private final long id;
        private final String name;
        private final String type;
        private final int age;
        private final String status;
    }

}