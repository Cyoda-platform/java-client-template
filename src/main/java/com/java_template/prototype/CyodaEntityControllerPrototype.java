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
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "pet"; // assuming entity name is 'pet'
    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/search")
    public List<JsonNode> searchPets(@Valid @RequestBody SearchRequest request) {
        logger.info("Received search request: type='{}', status='{}'", request.getType(), request.getStatus());
        String statusParam = (request.getStatus() != null && !request.getStatus().isBlank()) ? request.getStatus() : "available";

        // Build conditions
        List<Condition> conditionsList = new ArrayList<>();
        if (request.getType() != null && !request.getType().isBlank()) {
            conditionsList.add(Condition.of("$.category.name", "IEQUALS", request.getType()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            conditionsList.add(Condition.of("$.status", "EQUALS", statusParam));
        } else {
            conditionsList.add(Condition.of("$.status", "EQUALS", "available"));
        }

        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND",
                conditionsList.toArray(new Condition[0]));

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                conditionRequest
        );

        ArrayNode petsNode = filteredItemsFuture.join();

        if (petsNode == null || !petsNode.isArray()) {
            logger.error("Invalid response from EntityService");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from EntityService");
        }

        List<JsonNode> results = new ArrayList<>();
        for (JsonNode pet : petsNode) {
            results.add(pet);
        }
        return results;
    }

    @GetMapping
    public List<JsonNode> getCachedPets() {
        logger.info("Returning all pets from EntityService");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode petsNode = itemsFuture.join();
        return List.copyOf(petsNode);
    }

    @PostMapping("/adopt")
    public AdoptionResponse adoptPet(@Valid @RequestBody AdoptionRequest request) {
        logger.info("Adopt pet request: petId={}, adopterName={}", request.getPetId(), request.getAdopterName());

        // Check if pet exists and if already adopted by checking a field 'adopted' or similar.
        // Since no direct method, try to get item and check adopterName presence.

        CompletableFuture<ObjectNode> petFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, request.getPetId());
        ObjectNode petNode = petFuture.join();
        if (petNode == null) {
            logger.error("Pet id={} not found", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (petNode.has("adopted") && petNode.get("adopted").asBoolean(false)) {
            logger.error("Pet {} already adopted", request.getPetId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pet already adopted");
        }

        // Update pet with adopted = true and adopterName
        petNode.put("adopted", true);
        petNode.put("adopterName", request.getAdopterName());
        petNode.put("adoptedAt", Instant.now().toString());

        CompletableFuture<java.util.UUID> updatedFuture = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                request.getPetId(),
                petNode
        );
        updatedFuture.join(); // wait for completion

        return new AdoptionResponse(true, "Pet adopted successfully", request.getPetId());
    }

    @GetMapping("/{id}")
    public JsonNode getPetById(@PathVariable("id") Long petId) {
        logger.info("Fetch details for id={}", petId);
        CompletableFuture<ObjectNode> petFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, petId);
        ObjectNode petNode = petFuture.join();
        if (petNode == null) {
            logger.error("Pet id={} not found", petId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return petNode;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("HTTP error: {}", ex.getStatusCode());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @Data
    public static class SearchRequest {
        @Size(max = 20)
        private String type;
        @Size(max = 20)
        private String status;
    }

    @Data
    public static class AdoptionRequest {
        @NotNull
        private Long petId;
        @NotBlank
        private String adopterName;
    }

    @Data
    public static class AdoptionResponse {
        private final boolean success;
        private final String message;
        private final Long petId;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}