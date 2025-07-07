package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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
    private final ObjectMapper objectMapper;

    private static final String ENTITY_NAME = "pet";

    @PostMapping("/fetch")
    public ResponseEntity<PetsResponse> fetchPets(@RequestBody @Valid FetchRequest fetchRequest) throws ExecutionException, InterruptedException {
        logger.info("Received fetch request with filters: type={}, status={}", fetchRequest.getType(), fetchRequest.getStatus());

        String statusQuery = (fetchRequest.getStatus() == null || fetchRequest.getStatus().isBlank())
                ? "available" : fetchRequest.getStatus().toLowerCase();

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

        List<Pet> pets = jsonNodeToPetList(itemsNode);

        logger.info("Fetched {} pets from EntityService", pets.size());
        return ResponseEntity.ok(new PetsResponse(pets));
    }

    @GetMapping
    public ResponseEntity<PetsResponse> getCachedPets() throws ExecutionException, InterruptedException {
        logger.info("Fetching all pets");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode itemsNode = itemsFuture.get();
        List<Pet> pets = jsonNodeToPetList(itemsNode);
        logger.info("Returning {} pets", pets.size());
        return ResponseEntity.ok(new PetsResponse(pets));
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptResponse> adoptPet(@RequestBody @Valid AdoptRequest adoptRequest) throws ExecutionException, InterruptedException {
        logger.info("Received adoption request for technicalId={}", adoptRequest.getPetId());

        Condition idCondition = Condition.of("$.technicalId", "EQUALS", adoptRequest.getPetId().toString());
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", idCondition);
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, searchCondition);
        ArrayNode foundItems = itemsFuture.get();
        if (foundItems == null || foundItems.isEmpty()) {
            logger.error("Pet with id {} not found", adoptRequest.getPetId());
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        ObjectNode petNode = (ObjectNode) foundItems.get(0);

        petNode.put("status", "adopted");

        UUID technicalId;
        try {
            technicalId = UUID.fromString(petNode.get("technicalId").asText());
        } catch (Exception e) {
            logger.error("Invalid technicalId format: {}", petNode.get("technicalId").asText(), e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid pet technicalId");
        }

        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, petNode);
        updatedIdFuture.get();

        logger.info("Pet with technicalId {} marked as adopted", technicalId);
        return ResponseEntity.ok(new AdoptResponse(true, "Pet adoption status updated."));
    }

    @PostMapping("/add")
    public ResponseEntity<AddPetResponse> addPet(@RequestBody @Valid Pet pet) throws ExecutionException, InterruptedException {
        logger.info("Adding new pet with name={}", pet.getName());

        ObjectNode entityNode = petToObjectNode(pet);

        CompletableFuture<UUID> addFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, entityNode);
        UUID id = addFuture.get();

        logger.info("Added new pet with technicalId={}", id);
        return ResponseEntity.ok(new AddPetResponse(id));
    }

    private List<Pet> jsonNodeToPetList(ArrayNode arrayNode) {
        if (arrayNode == null) return List.of();
        return arrayNode.findValues(null).stream()
                .filter(node -> node.isObject())
                .map(node -> jsonNodeToPet((ObjectNode) node))
                .collect(Collectors.toList());
    }

    private Pet jsonNodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        try {
            if (node.hasNonNull("technicalId")) pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid technicalId found in entity: {}", node.get("technicalId").asText());
        }
        if (node.hasNonNull("name")) pet.setName(node.get("name").asText());
        if (node.hasNonNull("status")) pet.setStatus(node.get("status").asText());
        if (node.hasNonNull("type")) pet.setType(node.get("type").asText());
        if (node.hasNonNull("photoUrls") && node.get("photoUrls").isArray()) {
            pet.setPhotoUrls(node.findValuesAsText("photoUrls"));
        }
        return pet;
    }

    private ObjectNode petToObjectNode(Pet pet) {
        return objectMapper.valueToTree(pet);
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
        public PetsResponse(List<Pet> pets) {
            this.pets = pets;
        }
    }

    @Data
    public static class AdoptResponse {
        private boolean success;
        private String message;
        public AdoptResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    @Data
    public static class AddPetResponse {
        private UUID technicalId;
        public AddPetResponse(UUID technicalId) {
            this.technicalId = technicalId;
        }
    }

    @Data
    public static class Pet {
        private UUID technicalId;
        private String name;
        private String status;
        private String type;
        private List<String> photoUrls;
    }
}