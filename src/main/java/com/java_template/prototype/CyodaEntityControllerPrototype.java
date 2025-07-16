package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entity/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME = "pet";
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    @Data
    public static class PetRequest {
        private String petId; // optional for new pets
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 100)
        private String category;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(max = 255)
        private String description;
    }

    @Data
    public static class PetDeleteRequest {
        @NotBlank
        private String petId;
    }

    @Data
    public static class PetSearchRequest {
        @Size(max = 100)
        private String name;
        @Size(max = 100)
        private String category;
        @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    public static class PetResponse {
        private boolean success;
        private Pet pet;
    }

    @Data
    public static class DeleteResponse {
        private boolean success;
        private String message;
    }

    @Data
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId; // internal id from entityService
        private String petId; // petId is a string version of technicalId
        private String name;
        private String category;
        private String status;
        private String description;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PetResponse addOrUpdatePet(@RequestBody @Valid PetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received addOrUpdatePet request: {}", request);
        Pet pet;
        UUID technicalId = null;

        if (request.getPetId() != null && !request.getPetId().isBlank()) {
            try {
                technicalId = UUID.fromString(request.getPetId());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid petId format");
            }
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
            ObjectNode existingPetNode = itemFuture.get();
            if (existingPetNode == null || existingPetNode.isEmpty()) {
                // fetch from external API
                try {
                    String url = PETSTORE_API_BASE + "/" + request.getPetId();
                    String response = restTemplate.getForObject(url, String.class);
                    JsonNode externalPetJson = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
                    pet = new Pet();
                    pet.setTechnicalId(technicalId);
                    pet.setPetId(request.getPetId());
                    pet.setName(request.getName());
                    pet.setCategory(request.getCategory());
                    pet.setStatus(request.getStatus());
                    pet.setDescription(request.getDescription() != null ? request.getDescription() : "A lovely pet!");
                    logger.info("Fetched pet from external API and created local pet: {}", pet);

                    // create in entityService
                    CompletableFuture<UUID> addedIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet);
                    UUID addedId = addedIdFuture.get();
                    pet.setTechnicalId(addedId);
                    pet.setPetId(addedId.toString());

                } catch (Exception ex) {
                    logger.error("Error fetching pet from external API for petId {}: {}", request.getPetId(), ex.getMessage());
                    throw new ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_GATEWAY,
                            "Failed to fetch pet data from external API"
                    );
                }
            } else {
                pet = convertObjectNodeToPet(existingPetNode);
                pet.setName(request.getName());
                pet.setCategory(request.getCategory());
                pet.setStatus(request.getStatus());
                pet.setDescription(request.getDescription());
                CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, pet.getTechnicalId(), pet);
                updatedIdFuture.get(); // wait for update
                logger.info("Updated existing pet in entityService: {}", pet);
            }
        } else {
            pet = new Pet();
            pet.setName(request.getName());
            pet.setCategory(request.getCategory());
            pet.setStatus(request.getStatus());
            pet.setDescription(request.getDescription() != null ? request.getDescription() : "A lovely pet!");
            CompletableFuture<UUID> addedIdFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet);
            UUID addedId = addedIdFuture.get();
            pet.setTechnicalId(addedId);
            pet.setPetId(addedId.toString());
            logger.info("Created new pet: {}", pet);
        }
        PetResponse response = new PetResponse();
        response.setSuccess(true);
        response.setPet(pet);
        return response;
    }

    @GetMapping
    public Collection<Pet> getPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode itemsNode = itemsFuture.get();
        List<Pet> pets = new ArrayList<>();
        if (itemsNode != null) {
            for (JsonNode node : itemsNode) {
                pets.add(convertObjectNodeToPet((ObjectNode) node));
            }
        }
        logger.info("Retrieving all pets, count={}", pets.size());
        return pets;
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Collection<Pet> searchPets(@RequestBody @Valid PetSearchRequest request) throws ExecutionException, InterruptedException {
        logger.info("Searching pets with criteria: {}", request);
        List<Condition> conditions = new ArrayList<>();
        if (request.getName() != null && !request.getName().isBlank()) {
            conditions.add(Condition.of("$.name", "ICONTAINS", request.getName()));
        }
        if (request.getCategory() != null && !request.getCategory().isBlank()) {
            conditions.add(Condition.of("$.category", "IEQUALS", request.getCategory()));
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            conditions.add(Condition.of("$.status", "IEQUALS", request.getStatus()));
        }

        SearchConditionRequest conditionRequest;
        if (conditions.isEmpty()) {
            conditionRequest = null;
        } else {
            conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
        }

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode filteredItems = filteredItemsFuture.get();
        List<Pet> results = new ArrayList<>();
        if (filteredItems != null) {
            for (JsonNode node : filteredItems) {
                results.add(convertObjectNodeToPet((ObjectNode) node));
            }
        }
        logger.info("Search returned {} results", results.size());
        return results;
    }

    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DeleteResponse deletePet(@RequestBody @Valid PetDeleteRequest request) throws ExecutionException, InterruptedException {
        logger.info("Deleting pet with petId: {}", request.getPetId());
        UUID technicalId;
        try {
            technicalId = UUID.fromString(request.getPetId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid petId format");
        }
        CompletableFuture<UUID> deletedIdFuture = entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
        UUID deletedId = deletedIdFuture.get();
        DeleteResponse response = new DeleteResponse();
        if (deletedId != null) {
            response.setSuccess(true);
            response.setMessage("Pet deleted successfully");
            logger.info("Pet deleted: {}", request.getPetId());
        } else {
            response.setSuccess(false);
            response.setMessage("Pet not found");
            logger.warn("Pet to delete not found: {}", request.getPetId());
        }
        return response;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getMessage());
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return errorBody;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unhandled exception: ", ex);
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("error", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR.toString());
        errorBody.put("message", "Internal server error");
        return errorBody;
    }

    private Pet convertObjectNodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        if (node.hasNonNull("technicalId")) {
            pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
            pet.setPetId(pet.getTechnicalId().toString());
        }
        if (node.hasNonNull("name")) {
            pet.setName(node.get("name").asText());
        }
        if (node.hasNonNull("category")) {
            pet.setCategory(node.get("category").asText());
        }
        if (node.hasNonNull("status")) {
            pet.setStatus(node.get("status").asText());
        }
        if (node.hasNonNull("description")) {
            pet.setDescription(node.get("description").asText());
        }
        return pet;
    }
}