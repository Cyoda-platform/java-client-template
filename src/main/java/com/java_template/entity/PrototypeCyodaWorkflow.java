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
import jakarta.validation.constraints.Positive;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("cyodaentityprototype/pets")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private UUID technicalId; // from entityService
        private Long id;
        private String name;
        private String type;
        private String status;
        private String[] photoUrls;
        private String[] tags;
    }

    @Data
    static class SearchRequest {
        private String type;
        private String status;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SearchResponse {
        private Pet[] pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AdoptionRequest {
        private String adoptionId;
        private UUID petTechnicalId;
        private Long petId;
        private String adopterName;
        private String adopterContact;
        private String status;
        private String message;
        private Instant requestedAt;
    }

    @Data
    static class AdoptRequestBody {
        @NotNull
        @Positive
        private Long petId;
        @NotBlank
        private String adopterName;
        @NotBlank
        private String adopterContact;
    }

    // Workflow function for 'pet' entity
    private CompletableFuture<JsonNode> processpet(JsonNode entity) {
        ObjectNode petNode = (ObjectNode) entity;
        if (petNode.has("status") && petNode.get("status").isTextual()) {
            petNode.put("status", petNode.get("status").asText().toLowerCase(Locale.ROOT));
        }
        // Additional normalization or async side-effects can be added here if needed
        return CompletableFuture.completedFuture(petNode);
    }

    // Workflow function for 'adoptionrequest' entity
    private CompletableFuture<JsonNode> processadoptionrequest(JsonNode entity) {
        ObjectNode adoptionEntity = (ObjectNode) entity;
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted during adoption request processing", e);
                return adoptionEntity;
            }

            adoptionEntity.put("status", "approved");
            adoptionEntity.put("message", "Your adoption request has been approved! Thank you.");

            if (!adoptionEntity.hasNonNull("petTechnicalId")) {
                logger.warn("AdoptionRequest entity missing petTechnicalId");
                return adoptionEntity;
            }

            UUID petTechnicalId;
            try {
                petTechnicalId = UUID.fromString(adoptionEntity.get("petTechnicalId").asText());
            } catch (Exception ex) {
                logger.error("Invalid petTechnicalId in adoption request: {}", adoptionEntity.get("petTechnicalId").asText(), ex);
                return adoptionEntity;
            }

            try {
                ObjectNode petNode = entityService.getItem("pet", ENTITY_VERSION, petTechnicalId).join();
                if (petNode != null) {
                    petNode.put("status", "sold");
                    entityService.updateItem("pet", ENTITY_VERSION, petTechnicalId, petNode).join();
                    logger.info("Pet {} status updated to sold due to adoption approval", petTechnicalId);
                } else {
                    logger.warn("Pet entity not found for petTechnicalId {}", petTechnicalId);
                }
            } catch (Exception e) {
                logger.error("Error updating pet status during adoption approval", e);
            }

            return adoptionEntity;
        });
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("Received search request: type='{}', status='{}', name='{}'",
                request.getType(), request.getStatus(), request.getName());

        List<Condition> conditionsList = new ArrayList<>();
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            conditionsList.add(Condition.of("$.status", "EQUALS", request.getStatus()));
        }
        if (request.getType() != null && !request.getType().isBlank()) {
            conditionsList.add(Condition.of("$.type", "IEQUALS", request.getType()));
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            conditionsList.add(Condition.of("$.name", "ICONTAINS", request.getName()));
        }
        SearchConditionRequest conditionRequest = conditionsList.isEmpty() ? null :
                SearchConditionRequest.group("AND", conditionsList.toArray(new Condition[0]));

        try {
            CompletableFuture<ArrayNode> itemsFuture = conditionRequest == null ?
                    entityService.getItems("pet", ENTITY_VERSION) :
                    entityService.getItemsByCondition("pet", ENTITY_VERSION, conditionRequest);

            ArrayNode itemsArray = itemsFuture.join();

            Pet[] petsArray = new Pet[itemsArray.size()];
            int idx = 0;
            for (JsonNode node : itemsArray) {
                Pet pet = mapObjectNodeToPet((ObjectNode) node);
                if (pet != null) {
                    petsArray[idx++] = pet;
                }
            }
            return ResponseEntity.ok(new SearchResponse(petsArray));
        } catch (Exception e) {
            logger.error("Error during pet search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error searching pets: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotNull @Positive Long id) {
        logger.info("Get pet by id: {}", id);

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", id));
        try {
            ArrayNode itemsArray = entityService.getItemsByCondition("pet", ENTITY_VERSION, conditionRequest).join();
            if (itemsArray.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + id);
            }
            Pet pet = mapObjectNodeToPet((ObjectNode) itemsArray.get(0));
            return ResponseEntity.ok(pet);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception e) {
            logger.error("Error retrieving pet by id", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error retrieving pet: " + e.getMessage());
        }
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptionRequest> adoptPet(@RequestBody @Valid AdoptRequestBody request) {
        logger.info("Adoption request received for petId={} by {}", request.getPetId(), request.getAdopterName());

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", request.getPetId()));
        ArrayNode petNodes = entityService.getItemsByCondition("pet", ENTITY_VERSION, conditionRequest).join();
        if (petNodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with id " + request.getPetId());
        }
        ObjectNode petNode = (ObjectNode) petNodes.get(0);
        Pet pet = mapObjectNodeToPet(petNode);

        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AdoptionRequest(
                            null,
                            pet.getTechnicalId(),
                            pet.getId(),
                            request.getAdopterName(),
                            request.getAdopterContact(),
                            "rejected",
                            "Pet is not available for adoption",
                            Instant.now()));
        }

        ObjectNode adoptionNode = objectMapper.createObjectNode();
        String adoptionId = UUID.randomUUID().toString();
        adoptionNode.put("adoptionId", adoptionId);
        adoptionNode.put("petTechnicalId", pet.getTechnicalId().toString());
        adoptionNode.put("petId", pet.getId());
        adoptionNode.put("adopterName", request.getAdopterName());
        adoptionNode.put("adopterContact", request.getAdopterContact());
        adoptionNode.put("status", "pending");
        adoptionNode.put("message", "Your adoption request is pending approval");
        adoptionNode.put("requestedAt", Instant.now().toString());

        entityService.addItem(
                "adoptionrequest",
                ENTITY_VERSION,
                adoptionNode,
                this::processadoptionrequest);

        AdoptionRequest adoptionRequest = new AdoptionRequest(
                adoptionId,
                pet.getTechnicalId(),
                pet.getId(),
                request.getAdopterName(),
                request.getAdopterContact(),
                "pending",
                "Your adoption request is pending approval",
                Instant.now());

        return ResponseEntity.ok(adoptionRequest);
    }

    @GetMapping("/adoptions/{adoptionId}")
    public ResponseEntity<AdoptionRequest> getAdoptionStatus(@PathVariable @NotBlank String adoptionId) {
        logger.info("Retrieve adoption status for id: {}", adoptionId);

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND",
                Condition.of("$.adoptionId", "EQUALS", adoptionId));
        ArrayNode adoptionNodes = entityService.getItemsByCondition("adoptionrequest", ENTITY_VERSION, conditionRequest).join();
        if (adoptionNodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adoption request not found with id " + adoptionId);
        }
        ObjectNode adoptionNode = (ObjectNode) adoptionNodes.get(0);

        AdoptionRequest adoptionRequest = new AdoptionRequest(
                adoptionNode.path("adoptionId").asText(null),
                adoptionNode.hasNonNull("petTechnicalId") ? UUID.fromString(adoptionNode.get("petTechnicalId").asText()) : null,
                adoptionNode.hasNonNull("petId") ? adoptionNode.get("petId").longValue() : null,
                adoptionNode.path("adopterName").asText(null),
                adoptionNode.path("adopterContact").asText(null),
                adoptionNode.path("status").asText(null),
                adoptionNode.path("message").asText(null),
                adoptionNode.hasNonNull("requestedAt") ? Instant.parse(adoptionNode.get("requestedAt").asText()) : null);

        return ResponseEntity.ok(adoptionRequest);
    }

    public CompletableFuture<UUID> addPetItem(Pet pet) {
        JsonNode petNode = objectMapper.valueToTree(pet);
        return entityService.addItem(
                "pet",
                ENTITY_VERSION,
                petNode,
                this::processpet
        );
    }

    private Pet mapObjectNodeToPet(ObjectNode petNode) {
        try {
            UUID technicalId = UUID.fromString(petNode.path("technicalId").asText());
            Long id = petNode.path("id").isIntegralNumber() ? petNode.path("id").longValue() : null;
            String name = petNode.path("name").asText("");
            String type = petNode.path("type").asText("");
            String status = petNode.path("status").asText("");
            String[] photoUrls = objectMapper.convertValue(petNode.path("photoUrls"), String[].class);
            List<String> tagNames = new ArrayList<>();
            if (petNode.has("tags") && petNode.get("tags").isArray()) {
                for (JsonNode tagNode : petNode.get("tags")) {
                    String tagName = tagNode.path("name").asText(null);
                    if (tagName != null) {
                        tagNames.add(tagName);
                    }
                }
            }
            String[] tags = tagNames.toArray(new String[0]);
            return new Pet(technicalId, id, name, type, status, photoUrls, tags);
        } catch (Exception e) {
            logger.error("Error mapping pet ObjectNode to Pet DTO", e);
            return null;
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "message", "Internal server error"
                ));
    }
}