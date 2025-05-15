package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.java_template.common.service.EntityService;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "pet";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow function for "pet" entity; async processing before persistence.
    private CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        logger.info("processPet workflow started for entity: {}", entity);

        // Detect if this entity is a query request (flag "query": true)
        if (entity.has("query") && entity.get("query").asBoolean(false)) {
            String queryType = entity.hasNonNull("type") ? entity.get("type").asText() : null;
            String queryStatus = entity.hasNonNull("status") ? entity.get("status").asText() : null;
            String queryName = entity.hasNonNull("name") ? entity.get("name").asText() : null;

            logger.info("processPet detected query request: type={}, status={}, name={}", queryType, queryStatus, queryName);

            String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=";
            if (queryStatus != null) {
                url += queryStatus;
            } else {
                url += "available,pending,sold";
            }

            return CompletableFuture.supplyAsync(() -> {
                try {
                    logger.info("Calling external Petstore API: {}", url);
                    String responseJson = restTemplate.getForObject(url, String.class);
                    JsonNode rootArray = objectMapper.readTree(responseJson);

                    List<ObjectNode> filteredPets = new ArrayList<>();
                    if (rootArray.isArray()) {
                        for (JsonNode petNode : rootArray) {
                            if (!(petNode instanceof ObjectNode)) continue;
                            ObjectNode petObj = (ObjectNode) petNode;

                            if (queryType != null) {
                                JsonNode categoryNode = petObj.path("category");
                                String petType = categoryNode.path("name").asText(null);
                                if (petType == null || !petType.equalsIgnoreCase(queryType)) {
                                    continue;
                                }
                            }
                            if (queryName != null) {
                                String petName = petObj.path("name").asText("");
                                if (!petName.toLowerCase().contains(queryName.toLowerCase())) {
                                    continue;
                                }
                            }
                            filteredPets.add(petObj);
                        }
                    } else {
                        logger.warn("Unexpected Petstore API response format");
                    }

                    logger.info("Filtered pets count: {}", filteredPets.size());

                    // Add filtered pets as new entities of entityModel "pet"
                    // Cannot use workflow on these adds to avoid recursion
                    List<CompletableFuture<UUID>> addFutures = new ArrayList<>();
                    for (ObjectNode pet : filteredPets) {
                        ObjectNode newPetEntity = objectMapper.createObjectNode();
                        newPetEntity.put("name", pet.path("name").asText(""));
                        newPetEntity.put("type", pet.path("category").path("name").asText(""));
                        newPetEntity.put("status", pet.path("status").asText(""));
                        ArrayNode photoUrls = objectMapper.createArrayNode();
                        if (pet.has("photoUrls") && pet.get("photoUrls").isArray()) {
                            pet.get("photoUrls").forEach(photoUrls::add);
                        }
                        newPetEntity.set("photoUrls", photoUrls);

                        CompletableFuture<UUID> future = entityService.addItem(
                                ENTITY_NAME,
                                ENTITY_VERSION,
                                newPetEntity,
                                null); // no workflow here to avoid recursion
                        addFutures.add(future);
                    }

                    CompletableFuture.allOf(addFutures.toArray(new CompletableFuture[0])).join();

                    entity.put("processedAt", System.currentTimeMillis());
                    entity.put("petsAddedCount", filteredPets.size());
                    entity.remove("query");

                    return entity;

                } catch (Exception e) {
                    logger.error("Exception in processPet workflow during query processing", e);
                    throw new RuntimeException(e);
                }
            });
        }

        // For normal pet entities, enrich entity before persistence
        if (!entity.has("createdAt")) {
            entity.put("createdAt", System.currentTimeMillis());
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Endpoint: POST /query - submit query entity with workflow to fetch and add pets asynchronously
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> queryPets(@RequestBody @Valid PetQueryRequest queryRequest) throws Exception {
        logger.info("Received pet query request: {}", queryRequest);

        ObjectNode queryEntity = objectMapper.createObjectNode();
        queryEntity.put("query", true);
        if (queryRequest.getType() != null) queryEntity.put("type", queryRequest.getType());
        if (queryRequest.getStatus() != null) queryEntity.put("status", queryRequest.getStatus());
        if (queryRequest.getName() != null) queryEntity.put("name", queryRequest.getName());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                queryEntity,
                this::processPet);

        UUID technicalId = idFuture.join();
        logger.info("Query entity persisted with technicalId: {}", technicalId);

        return ResponseEntity.ok(new QueryResponse("Pet query processed, pets added asynchronously"));
    }

    // Endpoint: GET / - get all pets
    @GetMapping
    public ResponseEntity<List<ObjectNode>> getAllPets() throws Exception {
        logger.info("Fetching all pets from entityService");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();

        List<ObjectNode> pets = new ArrayList<>();
        for (JsonNode node : items) {
            if (node instanceof ObjectNode) {
                pets.add((ObjectNode) node);
            }
        }
        return ResponseEntity.ok(pets);
    }

    // Endpoint: POST /add - add pet entity with workflow enrichment
    @PostMapping("/add")
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid PetAddRequest addRequest) throws Exception {
        logger.info("Adding new pet: {}", addRequest);

        ObjectNode petEntity = objectMapper.createObjectNode();
        petEntity.put("name", addRequest.getName());
        petEntity.put("type", addRequest.getType());
        petEntity.put("status", addRequest.getStatus());
        ArrayNode photoUrls = objectMapper.createArrayNode();
        addRequest.getPhotoUrls().forEach(photoUrls::add);
        petEntity.set("photoUrls", photoUrls);

        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                petEntity,
                this::processPet);

        UUID technicalId = idFuture.join();

        Long id = uuidToLong(technicalId);

        return ResponseEntity.ok(new AddPetResponse(id, "Pet added successfully"));
    }

    // Endpoint: GET /{id} - get pet by technicalId converted to Long
    @GetMapping("/{id}")
    public ResponseEntity<ObjectNode> getPetById(@PathVariable("id") @NotNull @Min(1) Long id) throws Exception {
        logger.info("Fetching pet by id {}", id);
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.join();

        for (JsonNode node : items) {
            if (node.has("technicalId")) {
                UUID technicalId = UUID.fromString(node.get("technicalId").asText());
                if (id.equals(uuidToLong(technicalId)) && node instanceof ObjectNode) {
                    return ResponseEntity.ok((ObjectNode) node);
                }
            }
        }
        logger.warn("Pet not found with id {}", id);
        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
    }

    // Utility: Convert UUID to Long for id representation; handles negative values gracefully
    private Long uuidToLong(UUID uuid) {
        if (uuid == null) return null;
        long val = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return val < 0 ? -val : val;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetQueryRequest {
        @Size(max = 50)
        private String type;

        @Pattern(regexp = "available|pending|sold")
        private String status;

        @Size(min = 1, max = 100)
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetAddRequest {
        @NotBlank
        @Size(max = 100)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String type;

        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;

        @NotNull
        @Size(min = 1)
        private List<@NotBlank String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryResponse {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPetResponse {
        private Long id;
        private String message;
    }
}