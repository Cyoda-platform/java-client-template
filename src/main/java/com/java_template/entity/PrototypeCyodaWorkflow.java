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
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("cyoda/pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private static final String EXTERNAL_PETSTORE_BASE = "https://petstore.swagger.io/v2/pet";
    private static final String ENTITY_NAME = "pet";

    public CyodaEntityControllerPrototype(EntityService entityService, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;

        private Long id;

        @NotBlank
        @Size(max = 50)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String type;

        @NotBlank
        @Size(max = 20)
        private String status;

        @NotNull
        @Size(min = 1)
        private String[] photoUrls;
    }

    @Data
    @NoArgsConstructor
    public static class SearchRequest {
        @Size(max = 30)
        private String type;

        @Size(max = 30)
        private String status;
    }

    @Data
    @NoArgsConstructor
    public static class IdRequest {
        @NotNull
        private UUID technicalId;
    }

    /**
     * Workflow function applied before persisting pet entity.
     * This function asynchronously processes the entity ObjectNode:
     * - Fires external async calls for add or update based on presence of technicalId
     * - Can modify entity state if needed
     * @param entity ObjectNode representing pet entity data
     * @return CompletableFuture<ObjectNode> processed entity to persist
     */
    public CompletableFuture<ObjectNode> processpet(ObjectNode entity) {
        logger.info("Executing workflow processpet for entity before persistence");

        UUID technicalId = null;
        if (entity.hasNonNull("technicalId")) {
            try {
                String idStr = entity.get("technicalId").asText();
                if (!idStr.isEmpty()) {
                    technicalId = UUID.fromString(idStr);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid technicalId format in entity: {}", e.getMessage());
            }
        }

        CompletableFuture.runAsync(() -> {
            try {
                ObjectNode ps = objectMapper.createObjectNode();
                ps.put("id", 0);
                var cat = ps.putObject("category");
                String petType = entity.hasNonNull("type") ? entity.get("type").asText() : "unknown";
                cat.put("id", 0);
                cat.put("name", petType);
                String petName = entity.hasNonNull("name") ? entity.get("name").asText() : "";
                ps.put("name", petName);

                ArrayNode photos = ps.putArray("photoUrls");
                if (entity.hasNonNull("photoUrls") && entity.get("photoUrls").isArray()) {
                    entity.get("photoUrls").forEach(photos::add);
                }

                String status = entity.hasNonNull("status") ? entity.get("status").asText() : "available";
                ps.put("status", status);

                if (technicalId == null) {
                    restTemplate.postForEntity(EXTERNAL_PETSTORE_BASE, ps.toString(), String.class);
                    logger.info("External addPet API called asynchronously for pet name '{}'", petName);
                } else {
                    restTemplate.put(EXTERNAL_PETSTORE_BASE, ps.toString());
                    logger.info("External updatePet API called asynchronously for technicalId {}", technicalId);
                }
            } catch (Exception e) {
                logger.error("Failed to call external petstore API asynchronously", e);
            }
        });

        if (!entity.hasNonNull("status") || entity.get("status").asText().isEmpty()) {
            entity.put("status", "available");
        }

        return CompletableFuture.completedFuture(entity);
    }

    @PostMapping("/search")
    public CompletableFuture<ResponseEntity<JsonNode>> searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("Received searchPets request with type='{}', status='{}'", request.getType(), request.getStatus());

        SearchConditionRequest conditionRequest;

        if (StringUtils.hasText(request.getType()) && StringUtils.hasText(request.getStatus())) {
            conditionRequest = SearchConditionRequest.group("AND",
                    Condition.of("$.type", "IEQUALS", request.getType()),
                    Condition.of("$.status", "IEQUALS", request.getStatus()));
        } else if (StringUtils.hasText(request.getType())) {
            conditionRequest = SearchConditionRequest.group("AND",
                    Condition.of("$.type", "IEQUALS", request.getType()));
        } else if (StringUtils.hasText(request.getStatus())) {
            conditionRequest = SearchConditionRequest.group("AND",
                    Condition.of("$.status", "IEQUALS", request.getStatus()));
        } else {
            conditionRequest = null;
        }

        CompletableFuture<ArrayNode> itemsFuture;

        if (conditionRequest != null) {
            itemsFuture = entityService.getItemsByCondition(
                    ENTITY_NAME,
                    ENTITY_VERSION,
                    conditionRequest);
        } else {
            itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        }

        return itemsFuture.thenApply(items -> ResponseEntity.ok(items));
    }

    @PostMapping("/add")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> addPet(@RequestBody @Valid Pet newPet) {
        logger.info("Adding new pet: {}", newPet);

        ObjectNode entityNode = objectMapper.valueToTree(newPet);

        return entityService.addItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                entityNode,
                this::processpet
        ).thenApply(technicalId -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("technicalId", technicalId);
            resp.put("message", "Pet added successfully");
            return ResponseEntity.ok(resp);
        });
    }

    @PostMapping("/update")
    public CompletableFuture<ResponseEntity<Map<String, String>>> updatePet(@RequestBody @Valid Pet updateRequest) {
        logger.info("Updating pet: {}", updateRequest);

        if (updateRequest.getTechnicalId() == null)
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Pet technicalId is required");

        ObjectNode entityNode = objectMapper.valueToTree(updateRequest);

        return entityService.updateItem(
                        ENTITY_NAME,
                        ENTITY_VERSION,
                        updateRequest.getTechnicalId(),
                        entityNode
                )
                .thenApply(updatedId -> ResponseEntity.ok(Map.of("message", "Pet updated successfully")));
    }

    @PostMapping("/delete")
    public CompletableFuture<ResponseEntity<Map<String, String>>> deletePet(@RequestBody @Valid IdRequest idRequest) {
        logger.info("Deleting pet with technicalId {}", idRequest.getTechnicalId());

        return entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, idRequest.getTechnicalId())
                .thenApply(deletedId -> ResponseEntity.ok(Map.of("message", "Pet deleted successfully")));
    }

    @GetMapping("/{technicalId}")
    public CompletableFuture<ResponseEntity<Pet>> getPetById(@PathVariable UUID technicalId) {
        logger.info("Retrieving pet by technicalId {}", technicalId);

        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenApply(objNode -> {
                    if (objNode == null || objNode.isEmpty()) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with technicalId " + technicalId);
                    }
                    try {
                        Pet pet = objectMapper.treeToValue(objNode, Pet.class);
                        pet.setTechnicalId(technicalId);
                        return ResponseEntity.ok(pet);
                    } catch (Exception e) {
                        throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Error mapping pet data: " + e.getMessage());
                    }
                });
    }
}