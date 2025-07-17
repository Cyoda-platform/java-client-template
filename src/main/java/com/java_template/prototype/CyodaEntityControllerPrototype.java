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
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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

@RestController
@Validated
@RequestMapping(path = "/cyoda/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private static final String ENTITY_NAME = "Pet";

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AddUpdatePetResponse> addOrUpdatePet(@RequestBody @Valid Pet pet) throws ExecutionException, InterruptedException {
        logger.info("Received add/update for pet with technicalId={}", pet.getTechnicalId());
        if (pet.getTechnicalId() == null) {
            UUID newId = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet).get();
            pet.setTechnicalId(newId);
            logger.info("Generated new pet technicalId={}", newId);
        } else {
            entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, pet.getTechnicalId(), pet).get();
        }
        CompletableFuture.runAsync(() -> enrichPetFromExternalApi(pet));
        return ResponseEntity.ok(new AddUpdatePetResponse(true, pet));
    }

    private void enrichPetFromExternalApi(Pet pet) {
        try {
            logger.info("Enriching pet {} from external API", pet.getName());
            String url = "https://petstore3.swagger.io/api/v3/pet/findByStatus?status=available";
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.hasNonNull("name") && pet.getName().equalsIgnoreCase(node.get("name").asText())) {
                        if (node.has("category") && node.get("category").has("name")) {
                            pet.setCategory(node.get("category").get("name").asText());
                        }
                        if (node.has("photoUrls")) {
                            List<String> photos = new ArrayList<>();
                            node.get("photoUrls").forEach(n -> photos.add(n.asText()));
                            pet.setPhotoUrls(photos);
                        }
                        if (node.has("tags")) {
                            List<String> tags = new ArrayList<>();
                            node.get("tags").forEach(n -> {
                                if (n.has("name")) tags.add(n.get("name").asText());
                            });
                            pet.setTags(tags);
                        }
                        if (node.has("status")) {
                            pet.setStatus(node.get("status").asText());
                        }
                        logger.info("Enriched pet {}", pet.getTechnicalId());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Enrichment failed for pet {}: {}", pet.getTechnicalId(), e.getMessage());
        }
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) throws ExecutionException, InterruptedException {
        logger.info("Retrieving pet technicalId={}", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, UUID.fromString(id));
        ObjectNode item = itemFuture.get();
        if (item == null || item.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        Pet pet = objectMapper.convertValue(item, Pet.class);
        pet.setTechnicalId(UUID.fromString(item.get("technicalId").asText()));
        return ResponseEntity.ok(pet);
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid PetSearchRequest req) throws ExecutionException, InterruptedException {
        logger.info("Searching pets name={}, status={}, category={}", req.getName(), req.getStatus(), req.getCategory());

        List<Condition> conditions = new ArrayList<>();
        if (req.getName() != null && !req.getName().isBlank()) {
            conditions.add(Condition.of("$.name", "IEQUALS", req.getName()));
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            conditions.add(Condition.of("$.status", "IEQUALS", req.getStatus()));
        }
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            conditions.add(Condition.of("$.category", "IEQUALS", req.getCategory()));
        }

        List<Pet> results = new ArrayList<>();
        if (!conditions.isEmpty()) {
            SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, conditionRequest);
            ArrayNode items = filteredItemsFuture.get();
            if (items != null) {
                for (JsonNode node : items) {
                    Pet pet = objectMapper.convertValue(node, Pet.class);
                    if (node.hasNonNull("technicalId"))
                        pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                    results.add(pet);
                }
            }
        } else {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
            ArrayNode items = itemsFuture.get();
            if (items != null) {
                for (JsonNode node : items) {
                    Pet pet = objectMapper.convertValue(node, Pet.class);
                    if (node.hasNonNull("technicalId"))
                        pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                    results.add(pet);
                }
            }
        }

        // External API search fallback as in original
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            try {
                String url = "https://petstore3.swagger.io/api/v3/pet/findByStatus?status=" + req.getStatus();
                String json = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(json);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        Pet p = parsePet(node);
                        if (p != null && matches(p, req)) results.add(p);
                    }
                }
            } catch (Exception e) {
                logger.error("External search failed: {}", e.getMessage());
            }
        }

        return ResponseEntity.ok(results);
    }

    private boolean matches(Pet pet, PetSearchRequest req) {
        if (req.getName() != null && !req.getName().isBlank()) {
            if (!pet.getName().equalsIgnoreCase(req.getName())) return false;
        }
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            if (!pet.getCategory().equalsIgnoreCase(req.getCategory())) return false;
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if (!pet.getStatus().equalsIgnoreCase(req.getStatus())) return false;
        }
        return true;
    }

    private Pet parsePet(JsonNode node) {
        try {
            Pet p = new Pet();
            if (node.hasNonNull("id")) p.setId(node.get("id").asText());
            if (node.hasNonNull("name")) p.setName(node.get("name").asText());
            if (node.has("category") && node.get("category").hasNonNull("name"))
                p.setCategory(node.get("category").get("name").asText());
            if (node.hasNonNull("status")) p.setStatus(node.get("status").asText());
            if (node.has("photoUrls")) {
                List<String> photos = new ArrayList<>();
                node.get("photoUrls").forEach(n -> photos.add(n.asText()));
                p.setPhotoUrls(photos);
            }
            if (node.has("tags")) {
                List<String> tags = new ArrayList<>();
                node.get("tags").forEach(n -> {
                    if (n.has("name")) tags.add(n.get("name").asText());
                });
                p.setTags(tags);
            }
            return p;
        } catch (Exception e) {
            logger.error("Parse failed: {}", e.getMessage());
            return null;
        }
    }

    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeletePetResponse> deletePet(@RequestBody @Valid DeletePetRequest req) throws ExecutionException, InterruptedException {
        logger.info("Deleting pet technicalId={}", req.getId());
        UUID id = UUID.fromString(req.getId());
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(ENTITY_NAME, ENTITY_VERSION, id);
        if (deletedItemId.get() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(new DeletePetResponse(true, "Pet deleted successfully"));
    }

    @Data
    public static class Pet {
        @JsonIgnore
        private UUID technicalId;

        private String id; // legacy or external id

        @NotBlank
        private String name;

        @Size(min = 1, max = 50)
        private String category;

        @Size(min = 1, max = 20)
        private String status;

        @Size(max = 10)
        private List<@NotBlank String> tags = new ArrayList<>();

        @Size(max = 10)
        private List<@NotBlank String> photoUrls = new ArrayList<>();
    }

    @Data
    public static class AddUpdatePetResponse {
        private final boolean success;
        private final Pet pet;
    }

    @Data
    public static class PetSearchRequest {
        @Size(max = 50)
        private String name;
        @Size(max = 20)
        private String status;
        @Size(max = 50)
        private String category;
    }

    @Data
    public static class DeletePetRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class DeletePetResponse {
        private final boolean success;
        private final String message;
    }
}