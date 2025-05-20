package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/cyoda-pets")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        logger.info("Received fetch request with type={} status={}", request.getType(), request.getStatus());

        ObjectNode filterNode = objectMapper.createObjectNode();
        if (request.getStatus() != null) {
            filterNode.put("status", request.getStatus());
        } else {
            filterNode.put("status", "available,pending,sold");
        }
        if (request.getType() != null) {
            filterNode.put("type", request.getType());
        }

        try {
            entityService.addItem("PetSummaryFetchRequest", ENTITY_VERSION, filterNode, this::processPetSummaryFetchRequest).get();
        } catch (Exception e) {
            logger.error("Error starting pet fetch workflow", e);
            return ResponseEntity.status(500).body(new FetchPetsResponse("Failed to start fetch workflow", 0));
        }

        return ResponseEntity.ok(new FetchPetsResponse("Pets data fetch started", 0));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PetSummaryWithId>> getPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("PetSummary", ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        List<PetSummaryWithId> pets = new ArrayList<>();
        for (JsonNode node : items) {
            PetSummaryWithId pet = objectMapper.convertValue(node, PetSummaryWithId.class);
            pets.add(pet);
        }
        logger.info("Returning {} pets", pets.size());
        return ResponseEntity.ok(pets);
    }

    @PostMapping(value = "/details", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FetchPetDetailsResponse> fetchPetDetails(@RequestBody @Valid FetchPetDetailsRequest request) {
        logger.info("Received fetch details request for petId={}", request.getPetId());

        ObjectNode petIdNode = objectMapper.createObjectNode();
        petIdNode.put("petId", request.getPetId());

        try {
            entityService.addItem("PetDetailsFetchRequest", ENTITY_VERSION, petIdNode, this::processPetDetailsFetchRequest).get();
            return ResponseEntity.ok(new FetchPetDetailsResponse("Pet details fetch started", request.getPetId()));
        } catch (Exception e) {
            logger.error("Error starting pet details fetch workflow", e);
            return ResponseEntity.status(500).body(new FetchPetDetailsResponse("Failed to start pet details fetch", request.getPetId()));
        }
    }

    @GetMapping(value = "/details/{technicalId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetDetailsWithId> getPetDetails(@PathVariable UUID technicalId) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetDetails", ENTITY_VERSION, technicalId);
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found " + technicalId);
        }
        PetDetailsWithId details = objectMapper.convertValue(node, PetDetailsWithId.class);
        return ResponseEntity.ok(details);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(ex.getReason()));
    }

    // Workflow function for PetSummaryFetchRequest entityModel
    private CompletableFuture<ObjectNode> processPetSummaryFetchRequest(ObjectNode entity) {
        String statusFilter = entity.has("status") ? entity.get("status").asText() : "available,pending,sold";
        String typeFilter = entity.has("type") ? entity.get("type").asText() : null;

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusFilter;
                logger.info("Fetching pets from external API: {}", url);
                String response = restTemplate.getForObject(url, String.class);
                JsonNode rootNode = objectMapper.readTree(response);

                if (rootNode == null || !rootNode.isArray()) {
                    logger.warn("No pets returned from external API.");
                    return entity;
                }

                List<ObjectNode> petsToAdd = new ArrayList<>();
                for (JsonNode petNode : rootNode) {
                    String petType = petNode.path("category").path("name").asText(null);
                    if (typeFilter == null || typeFilter.equalsIgnoreCase(petType)) {
                        ObjectNode petEntity = objectMapper.createObjectNode();
                        petEntity.put("id", petNode.path("id").asInt());
                        petEntity.put("name", petNode.path("name").asText(null));
                        petEntity.put("status", petNode.path("status").asText(null));
                        petEntity.put("type", petType);

                        if (petNode.has("photoUrls") && petNode.get("photoUrls").isArray()) {
                            petEntity.set("photoUrls", petNode.get("photoUrls"));
                        }
                        petsToAdd.add(petEntity);
                    }
                }

                if (!petsToAdd.isEmpty()) {
                    logger.info("Adding {} PetSummary entities", petsToAdd.size());
                    entityService.addItems("PetSummary", ENTITY_VERSION, petsToAdd, this::processPetSummary).get();
                } else {
                    logger.info("No PetSummary entities to add after filtering");
                }
            } catch (Exception e) {
                logger.error("Error in processPetSummaryFetchRequest workflow", e);
            }
            return entity;
        });
    }

    // Workflow function for PetSummary entityModel
    private CompletableFuture<ObjectNode> processPetSummary(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.hasNonNull("status")) {
                entity.put("status", "available");
            }
            return entity;
        });
    }

    // Workflow function for PetDetailsFetchRequest entityModel
    private CompletableFuture<ObjectNode> processPetDetailsFetchRequest(ObjectNode entity) {
        if (!entity.has("petId")) {
            logger.warn("processPetDetailsFetchRequest called without petId");
            return CompletableFuture.completedFuture(entity);
        }
        int petId = entity.get("petId").asInt();

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://petstore.swagger.io/v2/pet/" + petId;
                logger.info("Fetching pet details from external API: {}", url);
                String response = restTemplate.getForObject(url, String.class);
                if (response == null) {
                    logger.warn("No response from pet details API for petId={}", petId);
                    return entity;
                }
                JsonNode petNode = objectMapper.readTree(response);

                ObjectNode petDetailsEntity = objectMapper.createObjectNode();
                petDetailsEntity.put("id", petNode.path("id").asInt());
                petDetailsEntity.put("name", petNode.path("name").asText(null));
                petDetailsEntity.put("status", petNode.path("status").asText(null));
                petDetailsEntity.put("type", petNode.path("category").path("name").asText(null));

                if (petNode.has("category")) {
                    petDetailsEntity.set("category", petNode.get("category"));
                }
                if (petNode.has("photoUrls")) {
                    petDetailsEntity.set("photoUrls", petNode.get("photoUrls"));
                }
                if (petNode.has("tags")) {
                    petDetailsEntity.set("tags", petNode.get("tags"));
                }

                entityService.addItem("PetDetails", ENTITY_VERSION, petDetailsEntity, this::processPetDetails).get();

            } catch (Exception e) {
                logger.error("Error in processPetDetailsFetchRequest workflow", e);
            }
            return entity;
        });
    }

    // Workflow function for PetDetails entityModel
    private CompletableFuture<ObjectNode> processPetDetails(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!entity.hasNonNull("status")) {
                entity.put("status", "available");
            }
            return entity;
        });
    }

    @Data
    public static class FetchPetsRequest {
        @Size(max = 30)
        private String type;
        @Size(max = 30)
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class FetchPetsResponse {
        private String message;
        private int count;
    }

    @Data
    public static class FetchPetDetailsRequest {
        @Min(1)
        private int petId;
    }

    @Data
    @AllArgsConstructor
    public static class FetchPetDetailsResponse {
        private String message;
        private int petId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetSummary {
        private int id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetSummaryWithId {
        private UUID technicalId;
        private int id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetDetails {
        private int id;
        private String name;
        private String type;
        private String status;
        private Category category;
        private List<String> photoUrls;
        private List<Tag> tags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetDetailsWithId {
        private UUID technicalId;
        private int id;
        private String name;
        private String type;
        private String status;
        private Category category;
        private List<String> photoUrls;
        private List<Tag> tags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Category {
        private int id;
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Tag {
        private int id;
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
    }
}