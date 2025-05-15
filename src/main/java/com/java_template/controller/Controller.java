package com.java_template.controller;

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

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-pets")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, JobStatus> importJobs = Collections.synchronizedMap(new HashMap<>());

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // Initialization of sample data
    @PostConstruct
    public void initSampleData() {
        List<ObjectNode> samples = new ArrayList<>();
        samples.add(createPetNode(null, "Whiskers", "Cat", 3, "available", "Playful tabby cat"));
        samples.add(createPetNode(null, "Barkley", "Dog", 5, "adopted", "Loyal golden retriever"));

        try {
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
            Thread.currentThread().interrupt();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<ImportResponse> importPets(@RequestBody @Valid ImportRequest request) {
        logger.info("Received import request: {}", request);
        String jobId = UUID.randomUUID().toString();
        importJobs.put(jobId, new JobStatus("processing", Instant.now()));

        CompletableFuture.runAsync(() -> {
            try {
                String statusFilter = (request.getStatus() != null && !request.getStatus().isBlank()) ? request.getStatus() : "available";
                String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusFilter;
                logger.info("Fetching from external API: {}", url);
                String json = restTemplate.getForObject(url, String.class);
                if (json == null) {
                    throw new IllegalStateException("External API returned null response");
                }
                JsonNode array = objectMapper.readTree(json);
                if (array.isArray()) {
                    List<ObjectNode> pets = new ArrayList<>();
                    for (JsonNode node : array) {
                        ObjectNode petNode = mapJsonNodeToPetNode(node);
                        pets.add(petNode);
                    }
                    // Process pets asynchronously before bulk add
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
                    logger.info("Imported {} pets from external API", processedPets.size());
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

    @GetMapping
    public ResponseEntity<List<PetSummary>> getPets(
            @RequestParam(required = false) @Size(min = 1) String type,
            @RequestParam(required = false) @Size(min = 1) String status) throws Exception {
        logger.info("Listing pets with type={} and status={}", type, status);
        ArrayNode items = entityService.getItems("pet", ENTITY_VERSION).get();
        List<PetSummary> list = new ArrayList<>();
        for (JsonNode node : items) {
            if (node == null || node.isNull()) continue;
            String technicalId = node.hasNonNull("technicalId") ? node.get("technicalId").asText() : null;
            if (technicalId == null) continue;
            String pType = node.hasNonNull("type") ? node.get("type").asText() : null;
            String pStatus = node.hasNonNull("status") ? node.get("status").asText() : null;
            if ((type == null || (pType != null && pType.equalsIgnoreCase(type))) &&
                    (status == null || (pStatus != null && pStatus.equalsIgnoreCase(status)))) {
                String name = node.hasNonNull("name") ? node.get("name").asText() : null;
                int age = node.hasNonNull("age") ? node.get("age").asInt(0) : 0;
                list.add(new PetSummary(technicalId, name, pType, age, pStatus));
            }
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) throws Exception {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
        logger.info("Getting pet with id={} ", id);
        ObjectNode node = entityService.getItem("pet", ENTITY_VERSION, uuid).get();
        if (node == null || node.isEmpty(null)) {
            logger.error("Pet not found id={} ", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @PostMapping
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid AddPetRequest request) throws Exception {
        logger.info("Adding pet: {} ", request);
        ObjectNode petNode = createPetNode(null, request.getName(), request.getType(), request.getAge(), request.getStatus(), request.getDescription());

        CompletableFuture<UUID> idFuture = entityService.addItem("pet", ENTITY_VERSION, petNode);
        UUID technicalId = idFuture.get();

        return ResponseEntity.status(HttpStatus.CREATED).body(new AddPetResponse(technicalId.toString(), "Pet added successfully"));
    }

    @PostMapping("/{id}/update-status")
    public ResponseEntity<UpdateStatusResponse> updatePetStatus(
            @PathVariable @NotBlank String id,
            @RequestBody @Valid UpdateStatusRequest request) throws Exception {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID format");
        }
        logger.info("Updating status of pet id={} to {}", id, request.getStatus());
        ObjectNode node = entityService.getItem("pet", ENTITY_VERSION, uuid).get();
        if (node == null || node.isEmpty(null)) {
            logger.error("Pet not found id={} ", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        // Update status in-place
        node.put("status", request.getStatus());

        // Update without workflow since no additional processing specified
        entityService.updateItem("pet", ENTITY_VERSION, uuid, node).get();

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
        else node.put("description", "");
        return node;
    }

    private ObjectNode mapJsonNodeToPetNode(JsonNode node) {
        ObjectNode petNode = objectMapper.createObjectNode();
        petNode.put("name", node.hasNonNull("name") ? node.get("name").asText() : "Unknown");
        petNode.put("type", node.has("category") && node.get("category").hasNonNull("name")
                ? node.get("category").get("name").asText() : "unknown");
        petNode.put("age", 0);
        petNode.put("status", node.hasNonNull("status") ? node.get("status").asText() : "unknown");
        petNode.put("description", "");
        return petNode;
    }

    private CompletableFuture<ObjectNode> processPet(ObjectNode petNode) {
        // Example asynchronous processing, e.g., validation or enrichment
        return CompletableFuture.supplyAsync(() -> petNode);
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