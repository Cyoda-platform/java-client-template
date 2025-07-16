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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@Validated
@RequestMapping("/cyoda/pets")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "pet";

    @Data
    public static class ImportRequest {
        @Pattern(regexp = "https?://.*", message = "Must be a valid URL")
        private String sourceUrl;
    }

    @Data
    public static class ImportResponse {
        private int importedCount;
        private String status;
    }

    @Data
    public static class SearchRequest {
        @Size(max = 50)
        private String type;
        @Size(max = 20)
        private String status;
        @Size(max = 50)
        private String name;
    }

    @Data
    public static class AddPetRequest {
        @NotBlank(message = "Name is mandatory")
        private String name;
        @NotBlank(message = "Type is mandatory")
        private String type;
        @NotBlank(message = "Status is mandatory")
        private String status;
        private List<@NotBlank String> tags = new ArrayList<>();
    }

    @Data
    public static class AddPetResponse {
        private UUID id;
        private String message;
    }

    @Data
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String name;
        private String type;
        private String status;
        private List<String> tags = new ArrayList<>();
    }

    private CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        // Normalize status to lowercase if present
        if (entity.hasNonNull("status")) {
            String status = entity.get("status").asText();
            entity.put("status", status.toLowerCase(Locale.ROOT));
        }

        // Example: asynchronously add a log entity of different model "log"
        CompletableFuture<Void> logFuture = CompletableFuture.runAsync(() -> {
            try {
                ObjectNode logEntity = objectMapper.createObjectNode();
                logEntity.put("entityType", ENTITY_NAME);
                logEntity.put("event", "Pet processed before persistence");
                logEntity.put("timestamp", System.currentTimeMillis());
                logEntity.put("petName", entity.path("name").asText("unknown"));
                // Allowed: different entity model "log"
                entityService.addItem("log", ENTITY_VERSION, logEntity).join();
            } catch (Exception e) {
                logger.error("Failed to add log entity asynchronously in processPet", e);
            }
        });

        // Example: asynchronous enrichment
        CompletableFuture<Void> enrichFuture = CompletableFuture.runAsync(() -> {
            // Simulate enrichment logic
            entity.put("enrichedAt", System.currentTimeMillis());
        });

        // Combine all async tasks and complete with the modified entity
        return CompletableFuture.allOf(logFuture, enrichFuture)
                .thenApply(v -> entity);
    }

    @PostMapping("/import")
    public ResponseEntity<ImportResponse> importPets(@RequestBody @Valid ImportRequest request) throws Exception {
        String defaultUrl = "https://petstore.swagger.io/v2/pet/findByStatus?status=available";
        String sourceUrl = StringUtils.hasText(request.getSourceUrl()) ? request.getSourceUrl() : defaultUrl;
        logger.info("Importing pets from {}", sourceUrl);

        String json = restTemplate.getForObject(URI.create(sourceUrl), String.class);
        if (json == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch data from source URL");
        }
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "External API returned non-array");
        }

        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (JsonNode node : root) {
            ObjectNode petNode = objectMapper.createObjectNode();
            petNode.put("name", node.path("name").asText("Unnamed"));
            petNode.put("type", node.path("category").path("name").asText("unknown"));
            petNode.put("status", node.path("status").asText("unknown"));
            ArrayNode tagsArray = objectMapper.createArrayNode();
            for (JsonNode tagNode : node.path("tags")) {
                String tag = tagNode.path("name").asText(null);
                if (tag != null) tagsArray.add(tag);
            }
            petNode.set("tags", tagsArray);
            // Use the workflow function for each pet import
            futures.add(entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode, this::processPet));
        }

        // Wait for all imports to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        ImportResponse resp = new ImportResponse();
        resp.setImportedCount(futures.size());
        resp.setStatus("success");
        logger.info("Imported {} pets", futures.size());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/search")
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid SearchRequest req) throws Exception {
        logger.info("Searching pets type='{}' status='{}' name='{}'", req.getType(), req.getStatus(), req.getName());

        List<Condition> conditions = new ArrayList<>();
        if (req.getType() != null) {
            conditions.add(Condition.of("$.type", "IEQUALS", req.getType()));
        }
        if (req.getStatus() != null) {
            conditions.add(Condition.of("$.status", "IEQUALS", req.getStatus()));
        }
        if (req.getName() != null) {
            conditions.add(Condition.of("$.name", "ICONTAINS", req.getName()));
        }

        CompletableFuture<ArrayNode> filteredItemsFuture;
        if (conditions.isEmpty()) {
            filteredItemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        } else {
            SearchConditionRequest conditionGroup = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionGroup);
        }
        ArrayNode filteredItems = filteredItemsFuture.get();

        List<Pet> result = new ArrayList<>();
        for (JsonNode node : filteredItems) {
            Pet pet = objectMapper.treeToValue(node, Pet.class);
            if (node.hasNonNull("technicalId")) {
                pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
            }
            result.add(pet);
        }

        logger.info("Found {} pets", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotNull UUID id) throws Exception {
        logger.info("Get pet by id {}", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            logger.error("Pet {} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        pet.setTechnicalId(id);
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/add")
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid AddPetRequest req) throws Exception {
        logger.info("Adding pet name='{}' type='{}'", req.getName(), req.getType());
        ObjectNode petNode = objectMapper.createObjectNode();
        petNode.put("name", req.getName());
        petNode.put("type", req.getType());
        petNode.put("status", req.getStatus());
        ArrayNode tagsArray = petNode.putArray("tags");
        for (String tag : req.getTags()) {
            tagsArray.add(tag);
        }

        // Add pet with workflow function processPet
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode, this::processPet);
        UUID techId = idFuture.get();

        AddPetResponse resp = new AddPetResponse();
        resp.setId(techId);
        resp.setMessage("Pet added successfully");
        logger.info("Pet {} added", techId);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getAllPets() throws Exception {
        logger.info("Retrieving all pets");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode nodes = itemsFuture.get();
        List<Pet> pets = new ArrayList<>();
        for (JsonNode node : nodes) {
            Pet pet = objectMapper.treeToValue(node, Pet.class);
            if (node.hasNonNull("technicalId")) {
                pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
            }
            pets.add(pet);
        }
        logger.info("Retrieved {} pets", pets.size());
        return ResponseEntity.ok(pets);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> err = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> err.put(e.getField(), e.getDefaultMessage()));
        logger.error("Validation errors: {}", err);
        return ResponseEntity.badRequest().body(err);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason() != null ? ex.getReason() : "Error");
        logger.error("ResponseStatusException: {}", err);
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }
}