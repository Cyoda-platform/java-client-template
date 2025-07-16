package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping(path = "/entity/pets")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String ENTITY_NAME = "Pet";

    @Data
    public static class Pet {
        private UUID technicalId; // corresponds to unique id
        @NotBlank
        private String name;
        @NotBlank
        private String category;
        @Size(max = 5)
        private List<@NotBlank String> tags = new ArrayList<>();
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    public static class PetSearchRequest {
        private String category;
        private String status;
        @Size(max = 5)
        private List<@NotBlank String> tags = new ArrayList<>();
    }

    @Data
    public static class PetSearchResponse {
        private List<Pet> results = new ArrayList<>();
    }

    @Data
    public static class FunFactResponse {
        @NotBlank
        private String fact;
    }

    @Data
    public static class AddOrUpdatePetResponse {
        private boolean success;
        private Pet pet;
    }

    // Workflow function applied asynchronously before persistence.
    // Entity is an ObjectNode that can be modified directly.
    // You can add/get other entities (different entityModel).
    // Cannot add/update/delete current entityModel inside workflow to avoid recursion.
    private CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        logger.info("Starting workflow processPet for entity: {}", entity);

        // Validate external API if technicalId present
        if (entity.hasNonNull("technicalId")) {
            try {
                String idStr = entity.get("technicalId").asText();
                String url = "https://petstore3.swagger.io/api/v3/pet/" + idStr;
                String ext = restTemplate.getForObject(url, String.class);
                JsonNode node = objectMapper.readTree(ext);
                logger.info("External pet validation succeeded for id {}: {}", idStr, node);
                // Enrich entity with data from external source
                if (node.has("category") && node.get("category").has("name")) {
                    entity.put("category", node.get("category").get("name").asText());
                }
                if (node.has("tags") && node.get("tags").isArray()) {
                    ArrayNode tagsNode = objectMapper.createArrayNode();
                    for (JsonNode t : node.get("tags")) {
                        if (t.has("name")) {
                            tagsNode.add(t.get("name").asText());
                        }
                    }
                    entity.set("tags", tagsNode);
                }
            } catch (Exception ex) {
                logger.warn("External validation failed for id {}: {}", entity.get("technicalId").asText(), ex.getMessage());
                // Do not block persistence on external validation failure
            }
        }

        // Add secondary entity of different model as part of workflow (e.g. audit record)
        try {
            ObjectNode auditEntity = objectMapper.createObjectNode();
            auditEntity.put("petName", entity.path("name").asText());
            auditEntity.put("timestamp", System.currentTimeMillis());
            auditEntity.put("status", entity.path("status").asText());
            // Add audit entity asynchronously; different model so allowed
            entityService.addItem("PetAudit", ENTITY_VERSION, auditEntity);
        } catch (Exception ex) {
            logger.warn("Failed to add PetAudit entity: {}", ex.getMessage());
            // Continue without blocking persistence of main entity
        }

        // Additional async tasks or enrichment can be added here if needed

        return CompletableFuture.completedFuture(entity);
    }

    @PostMapping
    public ResponseEntity<AddOrUpdatePetResponse> addOrUpdatePet(@RequestBody @Valid Pet petRequest) throws ExecutionException, InterruptedException {
        logger.info("POST /entity/pets request: {}", petRequest);

        // Convert Pet POJO to ObjectNode for entityService usage
        ObjectNode entityNode = objectMapper.valueToTree(petRequest);

        UUID technicalId = petRequest.getTechnicalId();

        if (technicalId != null) {
            // Update existing entity - no workflow applied on update per specification
            CompletableFuture<UUID> updatedFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, entityNode);
            UUID updatedId = updatedFuture.get();
            petRequest.setTechnicalId(updatedId);
        } else {
            // Add new entity with workflow applied
            CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entityNode, this::processPet);
            UUID newId = idFuture.get();
            petRequest.setTechnicalId(newId);
        }

        AddOrUpdatePetResponse resp = new AddOrUpdatePetResponse();
        resp.setSuccess(true);
        resp.setPet(petRequest);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("GET /entity/pets/{} request", id);
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format: {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pet ID format");
        }
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, uuid);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/search")
    public ResponseEntity<PetSearchResponse> searchPets(@RequestBody @Valid PetSearchRequest searchRequest) throws ExecutionException, InterruptedException {
        logger.info("POST /entity/pets/search request: {}", searchRequest);

        List<Condition> conditions = new ArrayList<>();

        if (searchRequest.getCategory() != null) {
            conditions.add(Condition.of("$.category", "IEQUALS", searchRequest.getCategory()));
        }
        if (searchRequest.getStatus() != null) {
            conditions.add(Condition.of("$.status", "IEQUALS", searchRequest.getStatus()));
        }
        if (searchRequest.getTags() != null && !searchRequest.getTags().isEmpty()) {
            for (String tag : searchRequest.getTags()) {
                conditions.add(Condition.of("$.tags", "ICONTAINS", tag));
            }
        }

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode filteredNodes = filteredItemsFuture.get();
        List<Pet> filtered = new ArrayList<>();
        if (filteredNodes != null) {
            for (JsonNode node : filteredNodes) {
                filtered.add(objectMapper.treeToValue(node, Pet.class));
            }
        }

        // External enrichment remains in controller for external pets from petstore API
        try {
            String url = "https://petstore3.swagger.io/api/v3/pet/findByStatus?status=available";
            String ext = restTemplate.getForObject(url, String.class);
            JsonNode arr = objectMapper.readTree(ext);
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    Pet extPet = new Pet();
                    extPet.setTechnicalId(null);
                    extPet.setName(node.path("name").asText(null));
                    if (node.has("category") && node.get("category").has("name")) {
                        extPet.setCategory(node.get("category").get("name").asText());
                    }
                    List<String> tags = new ArrayList<>();
                    if (node.has("tags") && node.get("tags").isArray()) {
                        for (JsonNode t : node.get("tags")) {
                            if (t.has("name")) {
                                tags.add(t.get("name").asText());
                            }
                        }
                    }
                    extPet.setTags(tags);
                    extPet.setStatus(node.path("status").asText(null));
                    if (matchesSearch(extPet, searchRequest)) {
                        filtered.add(extPet);
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("External search enrich failed: {}", ex.getMessage());
        }

        PetSearchResponse resp = new PetSearchResponse();
        resp.setResults(filtered);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/fun/fact")
    public ResponseEntity<FunFactResponse> randomPetFact() {
        logger.info("POST /entity/pets/fun/fact request");
        List<String> facts = List.of(
                "Cats sleep for 70% of their lives.",
                "Dogs have three eyelids.",
                "Goldfish can distinguish music genres.",
                "Rabbits can't vomit."
        );
        String fact = facts.get(new Random().nextInt(facts.size()));
        FunFactResponse resp = new FunFactResponse();
        resp.setFact(fact);
        return ResponseEntity.ok(resp);
    }

    private boolean matchesSearch(Pet pet, PetSearchRequest req) {
        if (req.getCategory() != null && (pet.getCategory() == null || !pet.getCategory().equalsIgnoreCase(req.getCategory()))) {
            return false;
        }
        if (req.getStatus() != null && (pet.getStatus() == null || !pet.getStatus().equalsIgnoreCase(req.getStatus()))) {
            return false;
        }
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            for (String tag : req.getTags()) {
                if (pet.getTags() == null || pet.getTags().stream().noneMatch(t -> t.equalsIgnoreCase(tag))) {
                    return false;
                }
            }
        }
        return true;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        logger.error("Handled error {}: {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Unexpected error occurred");
        logger.error("Unhandled exception: ", ex);
        return new ResponseEntity<>(err, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}