package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        private UUID technicalId; // internal id from entityService
        private String petId; // string form of technicalId
        private String name;
        private String category;
        private String status;
        private String description;
    }

    /**
     * Workflow function applied asynchronously before persistence.
     * Enriches the entity, fetches external data if necessary,
     * sets defaults, and can interact with other entity models.
     * Must NOT call add/update/delete on the "pet" model to avoid recursion.
     */
    private CompletableFuture<ObjectNode> processPet(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Workflow processPet started for entity: {}", entity);

                // Set default description if missing or blank
                if (!entity.hasNonNull("description") || entity.get("description").asText().isBlank()) {
                    entity.put("description", "A lovely pet!");
                }

                // Determine if external data fetch is needed:
                // Fetch if no technicalId (new entity) or essential fields missing or blank
                boolean needsExternalFetch = !entity.hasNonNull("technicalId");
                if (!needsExternalFetch) {
                    String name = entity.path("name").asText("");
                    String category = entity.path("category").asText("");
                    String status = entity.path("status").asText("");
                    if (name.isBlank() || category.isBlank() || status.isBlank()) {
                        needsExternalFetch = true;
                    }
                }

                if (needsExternalFetch) {
                    String petIdStr = entity.hasNonNull("petId") ? entity.get("petId").asText() : null;
                    if (petIdStr != null && !petIdStr.isBlank()) {
                        try {
                            String url = PETSTORE_API_BASE + "/" + petIdStr;
                            String response = restTemplate.getForObject(url, String.class);
                            if (response != null) {
                                JsonNode externalPetJson = objectMapper.readTree(response);
                                // Map external data to entity if missing or blank
                                if (!entity.hasNonNull("name") || entity.get("name").asText().isBlank()) {
                                    String externalName = externalPetJson.path("name").asText("");
                                    if (!externalName.isBlank()) entity.put("name", externalName);
                                }
                                if (!entity.hasNonNull("category") || entity.get("category").asText().isBlank()) {
                                    String externalCategory = externalPetJson.path("category").path("name").asText("");
                                    if (!externalCategory.isBlank()) entity.put("category", externalCategory);
                                }
                                if (!entity.hasNonNull("status") || entity.get("status").asText().isBlank()) {
                                    String externalStatus = externalPetJson.path("status").asText("");
                                    if (!externalStatus.isBlank()) entity.put("status", externalStatus);
                                }
                                if (!entity.hasNonNull("description") || entity.get("description").asText().isBlank()) {
                                    entity.put("description", "A lovely pet!");
                                }
                            }
                        } catch (Exception ex) {
                            logger.warn("Failed to fetch external pet data for petId={} in workflow: {}", petIdStr, ex.toString());
                            // Do not fail workflow; just log
                        }
                    }
                }

                // Additional workflow logic can be added here

                logger.debug("Workflow processPet finished for entity: {}", entity);
                return entity;
            } catch (Exception e) {
                logger.error("Error in workflow processPet", e);
                return entity; // Return entity unchanged on error to avoid blocking persistence
            }
        });
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PetResponse addOrUpdatePet(@RequestBody @Valid PetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received addOrUpdatePet request: {}", request);

        ObjectNode petNode = objectMapper.createObjectNode();

        UUID technicalId = null;
        if (request.getPetId() != null && !request.getPetId().isBlank()) {
            try {
                technicalId = UUID.fromString(request.getPetId());
                petNode.put("technicalId", technicalId.toString());
                petNode.put("petId", technicalId.toString());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid petId format");
            }
        }

        petNode.put("name", request.getName());
        petNode.put("category", request.getCategory());
        petNode.put("status", request.getStatus());
        if (request.getDescription() != null) {
            petNode.put("description", request.getDescription());
        }

        CompletableFuture<UUID> resultFuture;
        if (technicalId == null) {
            resultFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, petNode, this::processPet);
        } else {
            resultFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, petNode, this::processPet);
        }

        UUID persistedId = resultFuture.get();

        CompletableFuture<ObjectNode> persistedNodeFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, persistedId);
        ObjectNode persistedNode = persistedNodeFuture.get();

        Pet pet = convertObjectNodeToPet(persistedNode);

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
                if (node.isObject()) {
                    pets.add(convertObjectNodeToPet((ObjectNode) node));
                }
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

        SearchConditionRequest conditionRequest = null;
        if (!conditions.isEmpty()) {
            conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
        }

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
        ArrayNode filteredItems = filteredItemsFuture.get();
        List<Pet> results = new ArrayList<>();
        if (filteredItems != null) {
            for (JsonNode node : filteredItems) {
                if (node.isObject()) {
                    results.add(convertObjectNodeToPet((ObjectNode) node));
                }
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
            try {
                pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                pet.setPetId(pet.getTechnicalId().toString());
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid UUID format, leave petId null
            }
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