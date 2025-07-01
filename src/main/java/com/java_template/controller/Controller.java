package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("cyoda/pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EntityService entityService;

    private static final String ENTITY_NAME = "Pet";
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ProcessedPetsResponse fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        logger.info("Received fetchPets request type={} status={}", request.getType(), request.getStatus());

        try {
            String statusParam = request.getStatus();
            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusParam;
            logger.info("Calling external Petstore API: {}", url);
            String jsonResponse = restTemplate.getForObject(url, String.class);
            if (jsonResponse == null) {
                logger.error("Petstore API returned null response");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned null");
            }
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            List<ObjectNode> petsToAdd = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    String type = petNode.hasNonNull("category") && petNode.get("category").hasNonNull("name")
                            ? petNode.get("category").get("name").asText().toLowerCase()
                            : "dog";

                    if (!"all".equalsIgnoreCase(request.getType()) &&
                            !request.getType().equalsIgnoreCase(type)) {
                        continue;
                    }

                    ObjectNode pet = objectMapper.createObjectNode();
                    pet.putNull("technicalId"); // new entity, no id yet
                    pet.put("name", petNode.hasNonNull("name") ? petNode.get("name").asText() : "Unnamed");
                    pet.put("type", type);
                    pet.put("status", petNode.hasNonNull("status") ? petNode.get("status").asText() : "available");
                    pet.put("age", (int) (Math.random() * 15) + 1);

                    petsToAdd.add(pet);
                }

                List<CompletableFuture<UUID>> futures = new ArrayList<>();
                for (ObjectNode pet : petsToAdd) {
                    CompletableFuture<UUID> idFuture = entityService.addItem(
                            ENTITY_NAME,
                            ENTITY_VERSION,
                            pet
                    );
                    futures.add(idFuture);
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                for (int i = 0; i < petsToAdd.size(); i++) {
                    UUID id = futures.get(i).get();
                    petsToAdd.get(i).put("technicalId", id.toString());
                }
            } else {
                logger.error("Unexpected JSON structure from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected Petstore API response");
            }
            return new ProcessedPetsResponse(petsToAdd);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error fetching pets: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error contacting Petstore API");
        } catch (Exception e) {
            logger.error("Error fetching pets: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error contacting Petstore API");
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PetsResponse getPets() throws ExecutionException, InterruptedException {
        logger.info("Fetching all pets from EntityService");
        CompletableFuture<List<ObjectNode>> itemsFuture = entityService.getItems(
                ENTITY_NAME,
                ENTITY_VERSION
        );
        List<ObjectNode> items = itemsFuture.get();
        if (items == null) {
            items = new ArrayList<>();
        }
        return new PetsResponse(items);
    }

    @PostMapping(value = "/adopt", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AdoptionResponse adoptPet(@RequestBody @Valid AdoptionRequest request) throws ExecutionException, InterruptedException {
        logger.info("Adoption request for petId={} by {}", request.getPetId(), request.getAdopterName());

        UUID technicalId;
        try {
            technicalId = UUID.fromString(request.getPetId());
        } catch (IllegalArgumentException e) {
            logger.error("Invalid petId format: {}", request.getPetId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid petId format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode petNode = itemFuture.get();
        if (petNode == null) {
            logger.error("Pet not found with id {}", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }

        if ("sold".equalsIgnoreCase(petNode.path("status").asText())) {
            logger.error("Pet {} already adopted", request.getPetId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pet already adopted");
        }

        petNode.put("status", "sold");

        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                technicalId,
                petNode
        );
        updatedIdFuture.get();

        ObjectNode adoptionRecord = objectMapper.createObjectNode();
        adoptionRecord.put("petId", request.getPetId());
        adoptionRecord.put("adopterName", request.getAdopterName());
        adoptionRecord.put("adoptionDate", request.getAdoptionDate().toString());

        entityService.addItem("AdoptionRecord", ENTITY_VERSION, adoptionRecord);

        logger.info("Adoption recorded for petId {}", request.getPetId());
        return new AdoptionResponse(true, "Pet adoption recorded successfully");
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ObjectNode getPetById(@PathVariable("id") @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Fetching pet {}", id);
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid pet id format: {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pet id format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode petNode = itemFuture.get();
        if (petNode == null) {
            logger.error("Pet not found {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return petNode;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    private String generateFunFactForPet(String type) {
        switch (type.toLowerCase()) {
            case "cat":
                return "Cats sleep 70% of their lives.";
            case "dog":
                return "Dogs have three eyelids.";
            default:
                return "This pet is unique and adorable!";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchPetsRequest {
        @NotNull
        @Pattern(regexp = "cat|dog|all", message = "type must be cat, dog, or all")
        private String type;
        @NotNull
        @Pattern(regexp = "available|pending|sold", message = "status must be available, pending, or sold")
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedPetsResponse {
        private List<ObjectNode> processedPets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetsResponse {
        private List<ObjectNode> pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionRequest {
        @NotBlank
        private String petId;
        @NotBlank
        private String adopterName;
        @NotNull
        private LocalDate adoptionDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionResponse {
        private boolean success;
        private String message;
    }
}
