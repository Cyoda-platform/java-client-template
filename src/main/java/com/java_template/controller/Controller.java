package com.java_template.entity;

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
        @NotNull
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
        log.info("Received sync request from source={} type={} status={}", request.getSource(), request.getType(), request.getStatus());

        if (!"petstore".equalsIgnoreCase(request.getSource())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unsupported source: " + request.getSource());
        }

        // Create a transient pet entity with sync flag to trigger workflow sync logic
        ObjectNode syncEntity = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        syncEntity.put("syncFromPetstore", true);
        if (request.getType() != null) syncEntity.put("type", request.getType());
        if (request.getStatus() != null) syncEntity.put("status", request.getStatus());

        // Add entity; workflow will perform sync asynchronously before persistence
        CompletableFuture<UUID> idFuture = entityService.addItem("pet", ENTITY_VERSION, syncEntity);
        UUID id = idFuture.join();

        return ResponseEntity.ok(new SyncResponse(0, "Sync started with job entity id=" + id));
    }

    @GetMapping
    public ResponseEntity<List<Pet>> getPets(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status
    ) {
        log.info("Fetching pets with filters type={} status={}", type, status);

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

        CompletableFuture<ArrayNode> itemsFuture = (condition != null)
                ? entityService.getItemsByCondition("pet", ENTITY_VERSION, condition)
                : entityService.getItems("pet", ENTITY_VERSION);

        ArrayNode arrayNode = itemsFuture.join();

        List<Pet> result = new ArrayList<>();
        for (var itemNode : arrayNode) {
            ObjectNode obj = (ObjectNode) itemNode;
            Pet pet = new Pet();
            pet.setTechnicalId(UUID.fromString(obj.path("technicalId").asText()));
            pet.setName(obj.path("name").asText(null));
            pet.setType(obj.path("type").asText(null));
            if (obj.hasNonNull("age")) {
                pet.setAge(obj.get("age").asInt());
            }
            pet.setStatus(obj.path("status").asText(null));
            result.add(pet);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add")
    public ResponseEntity<Pet> addPet(@RequestBody @Valid AddPetRequest request) {
        log.info("Adding new pet: {}", request);

        ObjectNode petNode = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        petNode.put("name", request.getName());
        petNode.put("type", request.getType());
        petNode.put("age", request.getAge());
        petNode.put("status", request.getStatus());

        CompletableFuture<UUID> idFuture = entityService.addItem("pet", ENTITY_VERSION, petNode);
        UUID technicalId = idFuture.join();

        Pet pet = new Pet(technicalId, request.getName(), request.getType(), request.getAge(), request.getStatus());
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/update")
    public ResponseEntity<Map<String, String>> updatePet(@RequestBody @Valid UpdatePetRequest request) {
        log.info("Updating pet: {}", request);

        CompletableFuture<ObjectNode> existingFuture = entityService.getItem("pet", ENTITY_VERSION, request.getId());
        ObjectNode existingNode = existingFuture.join();
        if (existingNode == null || existingNode.isEmpty(null)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id: " + request.getId());
        }

        // Update fields if provided
        if (request.getName() != null) existingNode.put("name", request.getName());
        if (request.getType() != null) existingNode.put("type", request.getType());
        if (request.getAge() != null) existingNode.put("age", request.getAge());
        if (request.getStatus() != null) existingNode.put("status", request.getStatus());

        CompletableFuture<UUID> updatedItemId = entityService.updateItem("pet", ENTITY_VERSION, request.getId(), existingNode);
        updatedItemId.join();

        return ResponseEntity.ok(Map.of("message", "Pet updated successfully"));
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> deletePet(@RequestBody @Valid DeletePetRequest request) {
        log.info("Deleting pet with id: {}", request.getId());

        CompletableFuture<ObjectNode> existingFuture = entityService.getItem("pet", ENTITY_VERSION, request.getId());
        ObjectNode existingNode = existingFuture.join();
        if (existingNode == null || existingNode.isEmpty(null)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id: " + request.getId());
        }

        CompletableFuture<UUID> deletedItemId = entityService.deleteItem("pet", ENTITY_VERSION, request.getId());
        deletedItemId.join();

        return ResponseEntity.ok(Map.of("message", "Pet deleted successfully"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "message", "Internal server error"));
    }
}
