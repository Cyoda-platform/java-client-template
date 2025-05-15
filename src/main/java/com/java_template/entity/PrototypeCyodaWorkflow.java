```java
package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // Workflow function to process pet entity before persistence
    private CompletableFuture<Pet> processPet(Pet pet) {
        // Example processing: you can modify pet here asynchronously
        // For demo, just return the pet as is wrapped in completed future
        return CompletableFuture.completedFuture(pet);
    }

    @PostConstruct
    public void initSampleData() {
        // Import sample pets into entityService asynchronously
        List<Pet> samples = Arrays.asList(
                new Pet(null, "Whiskers", "Cat", 3, "available", "Playful tabby cat"),
                new Pet(null, "Barkley", "Dog", 5, "adopted", "Loyal golden retriever")
        );
        try {
            // Use workflow function processPet for each item before addItems
            // Since addItems does not support workflow function directly, process items first then add
            List<CompletableFuture<Pet>> processedFutures = new ArrayList<>();
            for (Pet pet : samples) {
                processedFutures.add(processPet(pet));
            }
            CompletableFuture.allOf(processedFutures.toArray(new CompletableFuture[0])).get();

            List<Pet> processedPets = new ArrayList<>();
            for (CompletableFuture<Pet> future : processedFutures) {
                processedPets.add(future.get());
            }

            CompletableFuture<List<UUID>> futureIds = entityService.addItems("pet", ENTITY_VERSION, processedPets);
            futureIds.get(); // wait completion
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

        CompletableFuture.runAsync(() -> {
            try {
                String statusFilter = (request.getStatus() != null) ? request.getStatus() : "available";
                String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusFilter;
                logger.info("Fetching from external API: {}", url);
                String json = restTemplate.getForObject(url, String.class);
                JsonNode array = objectMapper.readTree(json);
                if (array.isArray()) {
                    List<Pet> pets = new ArrayList<>();
                    for (JsonNode node : array) {
                        Pet pet = mapJsonNodeToPet(node);
                        pets.add(pet);
                    }

                    // Process each pet asynchronously before adding
                    List<CompletableFuture<Pet>> processedFutures = new ArrayList<>();
                    for (Pet pet : pets) {
                        processedFutures.add(processPet(pet));
                    }
                    CompletableFuture.allOf(processedFutures.toArray(new CompletableFuture[0])).join();

                    List<Pet> processedPets = new ArrayList<>();
                    for (CompletableFuture<Pet> future : processedFutures) {
                        processedPets.add(future.join());
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
        Pet pet = new Pet(null, request.getName(), request.getType(), request.getAge(), request.getStatus(), request.getDescription());

        // Use new addItem signature with workflow function processPet
        CompletableFuture<UUID> idFuture = entityService.addItem("pet", ENTITY_VERSION, pet, this::processPet);
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
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        pet.setStatus(request.getStatus());
        entityService.updateItem("pet", ENTITY_VERSION, technicalId, pet).get();
        return ResponseEntity.ok(new UpdateStatusResponse(id, request.getStatus(), "Status updated successfully"));
    }

    private Pet mapJsonNodeToPet(JsonNode node) {
        try {
            String id = node.has("id") ? String.valueOf(node.get("id").asLong()) : null;
            String name = node.has("name") ? node.get("name").asText() : "Unknown";
            String type = node.has("category") && node.get("category").has("name")
                    ? node.get("category").get("name").asText() : "unknown";
            String status = node.has("status") ? node.get("status").asText() : "unknown";
            return new Pet(null, name, type, 0, status, null); // id null to let entityService assign technicalId
        } catch (Exception e) {
            logger.error("Mapping error", e);
            return new Pet(null, "Unknown", "unknown", 0, "unknown", null);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        @JsonIgnore
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

### Explanation of changes:
- Added a new private method `processPet(Pet pet): CompletableFuture<Pet>` that implements the required workflow function. It asynchronously processes the pet entity before persistence (currently a no-op that returns the pet immediately).
- Updated `addPet` method to call the new `entityService.addItem` method that accepts the workflow function as the fourth parameter, passing `this::processPet`.
- Updated sample data initialization and importPets methods to process pets asynchronously before bulk adding because `addItems` does not have a workflow parameter in the provided code. So items are processed before being passed.
- Kept all other existing behavior unchanged.

This meets the new requirement that `entityService.addItem` requires a workflow function that processes the entity before persistence.