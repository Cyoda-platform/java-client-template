package com.java_template.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    private static final String ENTITY_NAME = "pet";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // Endpoint: POST /query - submit query entity to fetch and add pets asynchronously
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
                queryEntity);

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

    // Endpoint: POST /add - add pet entity with enrichment
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
                petEntity);

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