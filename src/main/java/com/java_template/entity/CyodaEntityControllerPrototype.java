package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/cyoda/pets")
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private static final String ENTITY_NAME = "pet";

    @PostMapping("/fetch")
    public ResponseEntity<PetsResponse> fetchPets(@RequestBody @Valid FetchRequest fetchRequest) throws ExecutionException, InterruptedException {
        logger.info("Received fetch request with filters: type={}, status={}", fetchRequest.getType(), fetchRequest.getStatus());

        String statusQuery = (fetchRequest.getStatus() == null || fetchRequest.getStatus().isBlank())
                ? "available" : fetchRequest.getStatus().toLowerCase();

        // Build conditions for querying pets
        Condition statusCondition = Condition.of("$.status", "IEQUALS", statusQuery);
        SearchConditionRequest conditionRequest;

        if (fetchRequest.getType() == null || fetchRequest.getType().isBlank()) {
            conditionRequest = SearchConditionRequest.group("AND", statusCondition);
        } else {
            Condition typeCondition = Condition.of("$.type", "IEQUALS", fetchRequest.getType());
            conditionRequest = SearchConditionRequest.group("AND", statusCondition, typeCondition);
        }

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode itemsNode = itemsFuture.get();

        // Convert ArrayNode to List<Pet>
        List<Pet> pets = itemsNode.findValuesAsText("technicalId").isEmpty() ? List.of() :
                JsonNodeToPetList(itemsNode);

        logger.info("Fetched {} pets from EntityService", pets.size());
        return ResponseEntity.ok(new PetsResponse(pets));
    }

    @GetMapping
    public ResponseEntity<PetsResponse> getCachedPets() throws ExecutionException, InterruptedException {
        logger.info("Fetching all pets");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode itemsNode = itemsFuture.get();
        List<Pet> pets = JsonNodeToPetList(itemsNode);
        logger.info("Returning {} pets", pets.size());
        return ResponseEntity.ok(new PetsResponse(pets));
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptResponse> adoptPet(@RequestBody @Valid AdoptRequest adoptRequest) throws ExecutionException, InterruptedException {
        logger.info("Received adoption request for technicalId={}", adoptRequest.getPetId());

        UUID petUUID = new UUID(adoptRequest.getPetId() >> 64, adoptRequest.getPetId()); // converting Long to UUID is not valid, better lookup by condition
        // Since original id is Long and technicalId is UUID, we must search by condition to find the item

        Condition idCondition = Condition.of("$.technicalId", "EQUALS", adoptRequest.getPetId().toString());
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", idCondition);
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, searchCondition);
        ArrayNode foundItems = itemsFuture.get();
        if (foundItems == null || foundItems.isEmpty()) {
            logger.error("Pet with id {} not found", adoptRequest.getPetId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        ObjectNode petNode = (ObjectNode) foundItems.get(0);
        UUID technicalId = UUID.fromString(petNode.get("technicalId").asText());

        // Update the pet adoption status (simulate by updating the pet with status = "adopted")
        Pet pet = JsonNodeToPet(petNode);
        pet.setStatus("adopted");
        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, pet);
        updatedIdFuture.get();

        logger.info("Pet with technicalId {} marked as adopted", technicalId);
        // TODO: fire-and-forget workflow/job to persist adoption and notify systems asynchronously
        return ResponseEntity.ok(new AdoptResponse(true, "Pet adoption status updated."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getStatusCode().toString(), ex.getReason()));
    }

    private List<Pet> JsonNodeToPetList(ArrayNode arrayNode) {
        return arrayNode
                .findValues(null)
                .stream()
                .map(this::JsonNodeToPet)
                .collect(Collectors.toList());
    }

    private Pet JsonNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        if (node.has("technicalId")) pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        if (node.has("name")) pet.setName(node.get("name").asText(null));
        if (node.has("status")) pet.setStatus(node.get("status").asText(null));
        if (node.has("type")) pet.setType(node.get("type").asText(null));
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            pet.setPhotoUrls(
                    petArrayToList(node.get("photoUrls"))
            );
        }
        return pet;
    }

    private List<String> petArrayToList(JsonNode arrayNode) {
        return arrayNode.findValuesAsText(null);
    }

    @Data
    public static class FetchRequest {
        @Size(max = 30)
        private String type;
        @Pattern(regexp = "available|pending|sold", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String status;
    }

    @Data
    public static class AdoptRequest {
        @NotNull
        private UUID petId;
    }

    @Data
    public static class PetsResponse {
        private List<Pet> pets;
        public PetsResponse(List<Pet> pets) { this.pets = pets; }
    }

    @Data
    public static class AdoptResponse {
        private boolean success;
        private String message;
        public AdoptResponse(boolean success, String message) { this.success = success; this.message = message; }
    }

    @Data
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String name;
        private String status;
        private String type;
        private List<String> photoUrls;
    }

    @Data
    public static class ErrorResponse {
        private String error;
        private String message;
        public ErrorResponse(String error, String message) { this.error = error; this.message = message; }
    }
}