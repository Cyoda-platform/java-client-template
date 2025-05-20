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

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        logger.info("Received fetch request with type={} status={}", request.getType(), request.getStatus());
        CompletableFuture.runAsync(() -> {
            try {
                String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" +
                        (request.getStatus() != null ? request.getStatus() : "available,pending,sold");
                String response = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
                JsonNode rootNode = objectMapper.readTree(response);
                List<PetSummary> filteredPets = new ArrayList<>();
                if (rootNode.isArray()) {
                    for (JsonNode petNode : rootNode) {
                        PetSummary pet = parsePetSummary(petNode);
                        if (request.getType() == null || request.getType().equalsIgnoreCase(pet.getType())) {
                            filteredPets.add(pet);
                        }
                    }
                }
                // Convert pets to list of Object to pass to entityService
                List<PetSummary> petsToAdd = filteredPets;
                // Add pets to external EntityService
                entityService.addItems("PetSummary", ENTITY_VERSION, petsToAdd).get();
                logger.info("Processed {} pets", filteredPets.size());
            } catch (Exception e) {
                logger.error("Error fetching pets", e);
            }
        });
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
    public ResponseEntity<FetchPetDetailsResponse> fetchPetDetails(@RequestBody @Valid FetchPetDetailsRequest request) throws Exception {
        logger.info("Fetching details for petId={}", request.getPetId());
        String url = "https://petstore.swagger.io/v2/pet/" + request.getPetId();
        String response = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
        JsonNode petNode = objectMapper.readTree(response);
        PetDetails details = parsePetDetails(petNode);
        UUID technicalId = entityService.addItem("PetDetails", ENTITY_VERSION, details).get();
        return ResponseEntity.ok(new FetchPetDetailsResponse("Pet details fetched and stored", request.getPetId()));
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

    private PetSummary parsePetSummary(JsonNode node) {
        PetSummary pet = new PetSummary();
        pet.setId(node.path("id").asInt());
        pet.setName(node.path("name").asText(null));
        pet.setStatus(node.path("status").asText(null));
        pet.setType(node.path("category").path("name").asText(null));
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            pet.setPhotoUrls(objectMapper.convertValue(node.get("photoUrls"), List.class));
        }
        return pet;
    }

    private PetDetails parsePetDetails(JsonNode node) {
        PetDetails details = new PetDetails();
        details.setId(node.path("id").asInt());
        details.setName(node.path("name").asText(null));
        details.setStatus(node.path("status").asText(null));
        details.setType(node.path("category").path("name").asText(null));
        if (node.has("category")) {
            JsonNode c = node.get("category");
            details.setCategory(new Category(c.path("id").asInt(), c.path("name").asText(null)));
        }
        if (node.has("photoUrls")) {
            details.setPhotoUrls(objectMapper.convertValue(node.get("photoUrls"), List.class));
        }
        if (node.has("tags")) {
            details.setTags(objectMapper.convertValue(node.get("tags"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Tag.class)));
        }
        return details;
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