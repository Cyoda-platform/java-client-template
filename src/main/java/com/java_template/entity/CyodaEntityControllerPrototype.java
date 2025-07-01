package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/cyoda/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class CyodaEntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();

    private final EntityService entityService;

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class Pet {
        @JsonIgnore
        private UUID technicalId;
        private String name;
        private String type;
        private Integer age;
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class SyncRequest {
        @NotBlank
        private String source;
        private String type;
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class SyncResponse {
        private int syncedCount;
        private String message;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class AddPetRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotNull @Min(0)
        private Integer age;
        @NotBlank
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class UpdatePetRequest {
        @NotBlank
        private UUID id;
        private String name;
        private String type;
        @Min(0)
        private Integer age;
        private String status;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class DeletePetRequest {
        @NotNull
        private UUID id;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class JobStatus {
        private String status;
        private Instant requestedAt;
    }

    private final Map<String, JobStatus> syncJobs = new ConcurrentHashMap<>();

    @PostMapping("/sync")
    public ResponseEntity<SyncResponse> syncPets(@RequestBody @Valid SyncRequest request) {
        logger.info("Received sync request from source={} type={} status={}",
                request.getSource(), request.getType(), request.getStatus());
        if (!"petstore".equalsIgnoreCase(request.getSource())) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported source: " + request.getSource()
            );
        }
        String jobId = UUID.randomUUID().toString();
        syncJobs.put(jobId, new JobStatus("processing", Instant.now()));
        CompletableFuture.runAsync(() -> {
            try {
                int count = performPetstoreSync(request.getType(), request.getStatus());
                syncJobs.put(jobId, new JobStatus("completed", Instant.now()));
                logger.info("Sync job {} completed. {} pets synced.", jobId, count);
            } catch (Exception e) {
                syncJobs.put(jobId, new JobStatus("failed", Instant.now()));
                logger.error("Sync job {} failed: {}", jobId, e.getMessage(), e);
            }
        });
        return ResponseEntity.ok(new SyncResponse(0, "Sync started, jobId=" + jobId));
    }

    private int performPetstoreSync(String typeFilter, String statusFilter) throws Exception {
        String statusParam = statusFilter != null ? statusFilter : "available";
        URI uri = new URI("https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam);
        logger.info("Fetching pets from Petstore API: {}", uri);
        String raw = restTemplate.getForObject(uri, String.class);
        if (raw == null) throw new IllegalStateException("Empty response from Petstore API");
        var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
        if (!root.isArray()) throw new IllegalStateException("Unexpected response format");
        List<Pet> petsToAdd = new ArrayList<>();
        for (var node : root) {
            String petType = node.path("category").path("name").asText(null);
            if (typeFilter != null && (petType == null || !typeFilter.equalsIgnoreCase(petType))) continue;
            String petName = node.path("name").asText("Unnamed");
            Integer petAge = null; // no age in Petstore API
            Pet pet = new Pet(null, petName, petType, petAge, statusParam);
            petsToAdd.add(pet);
        }
        if (!petsToAdd.isEmpty()) {
            CompletableFuture<List<UUID>> idsFuture = entityService.addItems("pet", ENTITY_VERSION, petsToAdd);
            idsFuture.join(); // wait for completion
        }
        return petsToAdd.size();
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getPets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status
    ) {
        logger.info("Fetching pets with filters type={} status={}", type, status);
        SearchConditionRequest condition = null;
        if (type != null && status != null) {
            condition = SearchConditionRequest.group("AND",
                    Condition.of("$.type", "IEQUALS", type),
                    Condition.of("$.status", "IEQUALS", status));
        } else if (type != null) {
            condition = SearchConditionRequest.group("AND",
                    Condition.of("$.type", "IEQUALS", type));
        } else if (status != null) {
            condition = SearchConditionRequest.group("AND",
                    Condition.of("$.status", "IEQUALS", status));
        }
        CompletableFuture<ArrayNode> itemsFuture;
        if (condition != null) {
            itemsFuture = entityService.getItemsByCondition("pet", ENTITY_VERSION, condition);
        } else {
            itemsFuture = entityService.getItems("pet", ENTITY_VERSION);
        }
        ArrayNode arrayNode = itemsFuture.join();
        List<Pet> result = new ArrayList<>();
        for (var itemNode : arrayNode) {
            ObjectNode obj = (ObjectNode) itemNode;
            Pet pet = new Pet();
            pet.setTechnicalId(UUID.fromString(obj.path("technicalId").asText()));
            pet.setName(obj.path("name").asText(null));
            pet.setType(obj.path("type").asText(null));
            if (obj.has("age") && !obj.get("age").isNull()) {
                pet.setAge(obj.get("age").asInt());
            }
            pet.setStatus(obj.path("status").asText(null));
            result.add(pet);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add")
    public ResponseEntity<Pet> addPet(@RequestBody @Valid AddPetRequest request) {
        logger.info("Adding new pet: {}", request);
        Pet pet = new Pet(null, request.getName(), request.getType(), request.getAge(), request.getStatus());
        CompletableFuture<UUID> idFuture = entityService.addItem("pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.join();
        pet.setTechnicalId(technicalId);
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> updatePet(@RequestBody @Valid UpdatePetRequest request) {
        logger.info("Updating pet: {}", request);
        CompletableFuture<ObjectNode> existingFuture = entityService.getItem("pet", ENTITY_VERSION, request.getId());
        ObjectNode existingNode = existingFuture.join();
        if (existingNode == null || existingNode.isEmpty(null)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Pet not found with id: " + request.getId()
            );
        }
        Pet existingPet = new Pet();
        existingPet.setTechnicalId(request.getId());
        existingPet.setName(existingNode.path("name").asText(null));
        existingPet.setType(existingNode.path("type").asText(null));
        if (existingNode.has("age") && !existingNode.get("age").isNull()) {
            existingPet.setAge(existingNode.get("age").asInt());
        }
        existingPet.setStatus(existingNode.path("status").asText(null));

        if (request.getName() != null) existingPet.setName(request.getName());
        if (request.getType() != null) existingPet.setType(request.getType());
        if (request.getAge() != null) existingPet.setAge(request.getAge());
        if (request.getStatus() != null) existingPet.setStatus(request.getStatus());

        CompletableFuture<UUID> updatedItemId = entityService.updateItem("pet", ENTITY_VERSION, request.getId(), existingPet);
        updatedItemId.join();
        return ResponseEntity.ok(Map.of("message", "Pet updated successfully"));
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> deletePet(@RequestBody @Valid DeletePetRequest request) {
        logger.info("Deleting pet with id: {}", request.getId());
        CompletableFuture<ObjectNode> existingFuture = entityService.getItem("pet", ENTITY_VERSION, request.getId());
        ObjectNode existingNode = existingFuture.join();
        if (existingNode == null || existingNode.isEmpty(null)) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Pet not found with id: " + request.getId()
            );
        }
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem("pet", ENTITY_VERSION, request.getId());
        deletedItemId.join();
        return ResponseEntity.ok(Map.of("message", "Pet deleted successfully"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "message", "Internal server error"));
    }
}