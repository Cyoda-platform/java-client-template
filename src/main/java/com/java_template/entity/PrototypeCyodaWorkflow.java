```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-pets")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, JobStatus> importJobs = new HashMap<>();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /*
     * Workflow function to process pet entity before persistence.
     * This function will be called asynchronously right before the entity is persisted.
     * - Modifies the entity ObjectNode directly if needed.
     * - Can add/get other entityModels but cannot operate on the same entityModel to avoid recursion.
     * 
     * This replaces async tasks from controllers such as enrichment, external calls, etc.
     */
    private CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Processing pet entity in workflow: {}", petEntity);

                // Example: If the pet has no description, enrich description asynchronously
                if (!petEntity.hasNonNull("description") || petEntity.get("description").asText().trim().isEmpty()) {
                    String type = petEntity.has("type") ? petEntity.get("type").asText() : "unknown";
                    String name = petEntity.has("name") ? petEntity.get("name").asText() : "unknown";

                    // Add a generated description
                    String enrichedDescription = "A lovely " + type + " named " + name + ".";
                    petEntity.put("description", enrichedDescription);
                    logger.debug("Enriched description for pet: {}", enrichedDescription);
                }

                // Example: Side-effect: add a supplementary log entity (different entityModel)
                ObjectNode logEntity = objectMapper.createObjectNode();
                logEntity.put("timestamp", Instant.now().toString());
                logEntity.put("message", "Pet processed in workflow, name=" + petEntity.get("name").asText(""));
                logEntity.put("petType", petEntity.get("type").asText(""));
                // Add supplementary log entity asynchronously (different entityModel)
                entityService.addItem("processingLog", ENTITY_VERSION, logEntity).exceptionally(ex -> {
                    logger.error("Failed to add processingLog entity", ex);
                    return null;
                });

                // Could also retrieve other entities if needed for enrichment
                // e.g. entityService.getItem("someOtherEntityModel", ENTITY_VERSION, someId)...

                return petEntity;
            } catch (Exception e) {
                logger.error("Error in processPet workflow function", e);
                // Even on error, return the original entity to persist it as-is
                return petEntity;
            }
        });
    }

    /*
     * Workflow function to process pet entities before bulk persistence.
     * For bulk adds, entityService.addItems does not accept workflow function,
     * so processing must be done explicitly in controller before calling addItems.
     * 
     * This method is provided for completeness but not used directly by entityService.
     */
    private CompletableFuture<ObjectNode> processPetForBulk(ObjectNode petEntity) {
        // For bulk, same logic can be applied, or just call processPet and join
        return processPet(petEntity);
    }

    @PostConstruct
    public void initSampleData() {
        // Import sample pets into entityService asynchronously
        List<ObjectNode> samples = new ArrayList<>();
        samples.add(createPetNode(null, "Whiskers", "Cat", 3, "available", "Playful tabby cat"));
        samples.add(createPetNode(null, "Barkley", "Dog", 5, "adopted", "Loyal golden retriever"));

        try {
            // Process each sample pet entity asynchronously before adding
            List<CompletableFuture<ObjectNode>> processedFutures = new ArrayList<>();
            for (ObjectNode petNode : samples) {
                processedFutures.add(processPet(petNode));
            }
            CompletableFuture.allOf(processedFutures.toArray(new CompletableFuture[0])).get();

            List<ObjectNode> processedPets = new ArrayList<>();
            for (CompletableFuture<ObjectNode> f : processedFutures) {
                processedPets.add(f.get());
            }

            entityService.addItems("pet", ENTITY_VERSION, processedPets).get();
            logger.info("Sample pets initialized via EntityService");
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to initialize sample pets", e);
        }
    }

    @PostMapping("/import") // must be first
    public ResponseEntity<ImportResponse> importPets(@RequestBody @Valid ImportRequest request) {
        logger.info("Received import request: {}", request);
        String jobId = UUID.randomUUID().toString();
        importJobs.put(jobId, new JobStatus("processing", Instant.now()));

        // Fire-and-forget async import job
        CompletableFuture.runAsync(() -> {
            try {
                String statusFilter = (request.getStatus() != null) ? request.getStatus() : "available";
                String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusFilter;
                logger.info("Fetching from external API: {}", url);
                String json = restTemplate.getForObject(url, String.class);
                JsonNode array = objectMapper.readTree(json);
                if (array.isArray()) {
                    List<ObjectNode> pets = new ArrayList<>();
                    for (JsonNode node : array) {
                        ObjectNode petNode = mapJsonNodeToPetNode(node);
                        pets.add(petNode);
                    }

                    // Process each pet asynchronously before adding
                    List<CompletableFuture<ObjectNode>> processedFutures = new ArrayList<>();
                    for (ObjectNode petNode : pets) {
                        processedFutures.add(processPet(petNode));
                    }
                    CompletableFuture.allOf(processedFutures.toArray(new CompletableFuture[0])).join();

                    List<ObjectNode> processedPets = new ArrayList<>();
                    for (CompletableFuture<ObjectNode> f : processedFutures) {
                        processedPets.add(f.join());
                    }

                    entityService.addItems("pet", ENTITY_VERSION, processedPets).get();
                    importJobs.put(jobId, new JobStatus("completed", Instant.now()));
                    logger.info("Imported {} pets", pets.size());
                } else {
                    importJobs.put(jobId, new JobStatus("failed", Instant.now()));
                    logger.error("External API response is not an array");
                }
            } catch (Exception e) {
                importJobs.put(jobId, new JobStatus("failed", Instant.now()));
                logger.error("Import failed", e);
            }
        });
        return ResponseEntity.ok(new ImportResponse(jobId, "Import started"));
    }

    @GetMapping // must be first
    public ResponseEntity<List<PetSummary>> getPets(
            @RequestParam(required = false) @Size(min = 1) String type,
            @RequestParam(required = false) @Size(min = 1) String status) throws Exception {
        logger.info("Listing pets type={} status={}", type, status);
        ArrayNode items = entityService.getItems("pet", ENTITY_VERSION).get();
        List<PetSummary> list = new ArrayList<>();
        for (JsonNode node : items) {
            String technicalId = node.get("technicalId").asText();
            String pType = node.has("type") ? node.get("type").asText() : null;
            String pStatus = node.has("status") ? node.get("status").asText() : null;
            if ((type == null || (pType != null && pType.equalsIgnoreCase(type))) &&
                    (status == null || (pStatus != null && pStatus.equalsIgnoreCase(status)))) {
                String name = node.has("name") ? node.get("name").asText() : null;
                int age = node.has("age") ? node.get("age").asInt() : 0;
                list.add(new PetSummary(technicalId, name, pType, age, pStatus));
            }
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}") // must be first
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) throws Exception {
        logger.info("Getting pet id={}", id);
        ObjectNode node = entityService.getItem("pet", ENTITY_VERSION, UUID.fromString(id)).get();
        if (node == null || node.isEmpty(null)) {
            logger.error("Pet not found id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @PostMapping // must be first
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid AddPetRequest request) throws Exception {
        logger.info("Adding pet: {}", request);
        ObjectNode petNode = createPetNode(null, request.getName(), request.getType(), request.getAge(), request.getStatus(), request.getDescription());

        // Use new addItem signature with workflow function processPet
        CompletableFuture<UUID> idFuture = entityService.addItem("pet", ENTITY_VERSION, petNode, this::processPet);
        UUID technicalId = idFuture.get();

        return ResponseEntity.status(HttpStatus.CREATED).body(new AddPetResponse(technicalId.toString(), "Pet added successfully"));
    }

    @PostMapping("/{id}/update-status") // must be first
    public ResponseEntity<UpdateStatusResponse> updatePetStatus(
            @PathVariable @NotBlank String id,
            @RequestBody @Valid UpdateStatusRequest request) throws Exception {
        logger.info("Updating status id={} to {}", id, request.getStatus());
        UUID technicalId = UUID.fromString(id);
        ObjectNode node = entityService.getItem("pet", ENTITY_VERSION, technicalId).get();
        if (node == null || node.isEmpty(null)) {
            logger.error("Pet not found id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        // Modify status directly on node
        node.put("status", request.getStatus());

        // Persist update with workflow (optional, here we do not process update workflow)
        entityService.updateItem("pet", ENTITY_VERSION, technicalId, node).get();

        return ResponseEntity.ok(new UpdateStatusResponse(id, request.getStatus(), "Status updated successfully"));
    }

    private ObjectNode createPetNode(String technicalId, String name, String type, int age, String status, String description) {
        ObjectNode node = objectMapper.createObjectNode();
        if (technicalId != null) node.put("technicalId", technicalId);
        node.put("name", name);
        node.put("type", type);
        node.put("age", age);
        node.put("status", status);
        if (description != null) node.put("description", description);
        return node;
    }

    private ObjectNode mapJsonNodeToPetNode(JsonNode node) {
        ObjectNode petNode = objectMapper.createObjectNode();
        petNode.put("name", node.has("name") ? node.get("name").asText() : "Unknown");
        petNode.put("type", node.has("category") && node.get("category").has("name")
                ? node.get("category").get("name").asText() : "unknown");
        petNode.put("age", 0);
        petNode.put("status", node.has("status") ? node.get("status").asText() : "unknown");
        petNode.put("description", "");
        return petNode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private String technicalId;
        private String name;
        private String type;
        private int age;
        private String status;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class PetSummary {
        private String id;
        private String name;
        private String type;
        private int age;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImportRequest {
        @NotBlank
        private String source;
        @Size(min = 1)
        private String type;
        @Size(min = 1)
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ImportResponse {
        private String jobId;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @Min(0)
        private int age;
        @NotBlank
        private String status;
        @Size(max = 255)
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AddPetResponse {
        private String id;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UpdateStatusRequest {
        @NotBlank
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class UpdateStatusResponse {
        private String id;
        private String newStatus;
        private String message;
    }

    @Data
    @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant timestamp;
    }
}
```

---

### Summary of changes and rationale:

- **Moved all async enrichment and supplementary logic from the controller endpoints into the `processPet(ObjectNode petEntity)` workflow function.**

- **Inside `processPet`:**
  - Enrich the pet entity's `description` if missing.
  - Add a supplementary entity (`processingLog`) to a different entity model asynchronously.
  - This function asynchronously processes the entity before persistence and can be extended further with other enrichment or side-effect logic.
  
- **Controller methods (`addPet`, `importPets`, `initSampleData`) now:**
  - Use the workflow function for each pet entity before persistence.
  - In bulk add scenarios (e.g., import, initSampleData), process entities asynchronously in the controller before calling `entityService.addItems` (which does not support workflow functions).
  
- **`updatePetStatus` simply modifies the existing entity node and persists it; no workflow needed here (could add if desired).**

- **This approach frees controllers from async enrichment and side effects, moves all pre-persist logic into workflow functions that run right before persistence, as required.**

- **All entity state changes are done by mutating the passed `ObjectNode` directly, per requirement.**

- **Supplementary entities are added via `entityService.addItem` on different entity models inside the workflow function, avoiding infinite recursion.**

This makes the code more robust, maintainable, and aligned with the new architecture requirements.