package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @PostMapping
    public ResponseEntity<AddUpdatePetResponse> addOrUpdatePet(@RequestBody @Valid PetRequest petRequest) {
        logger.info("Received addOrUpdatePet request: {}", petRequest);

        ObjectNode petNode = objectMapper.createObjectNode();
        if (petRequest.getId() != null && !petRequest.getId().isBlank()) {
            petNode.put("technicalId", petRequest.getId());
        }
        petNode.put("name", petRequest.getName());
        petNode.put("category", petRequest.getCategory());
        petNode.put("status", petRequest.getStatus());
        petNode.put("age", petRequest.getAge());
        petNode.put("breed", petRequest.getBreed());
        if (petRequest.getDescription() != null) {
            petNode.put("description", petRequest.getDescription());
        }

        CompletableFuture<UUID> idFuture;
        if (petRequest.getId() != null && !petRequest.getId().isBlank()) {
            ObjectNode existingPet = entityService.getItem("pet", ENTITY_VERSION, petRequest.getId()).join();
            if (existingPet != null && existingPet.hasNonNull("technicalId")) {
                entityService.updateItem("pet", ENTITY_VERSION, petRequest.getId(), petNode).join();
                logger.info("Updated pet with technicalId {}", petRequest.getId());
                idFuture = CompletableFuture.completedFuture(UUID.fromString(petRequest.getId()));
            } else {
                idFuture = entityService.addItem("pet", ENTITY_VERSION, petNode, this::processPet);
            }
        } else {
            idFuture = entityService.addItem("pet", ENTITY_VERSION, petNode, this::processPet);
        }

        UUID newId = idFuture.join();
        petNode.put("technicalId", newId.toString());

        Pet pet = convertObjectNodeToPet(petNode);
        return ResponseEntity.ok(new AddUpdatePetResponse(true, pet));
    }

    @PostMapping("/search")
    public ResponseEntity<SearchPetsResponse> searchPets(@RequestBody @Valid SearchPetsRequest searchRequest) {
        logger.info("Received searchPets request: {}", searchRequest);

        List<Pet> results = new ArrayList<>();

        if (StringUtils.hasText(searchRequest.getStatus())) {
            try {
                String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + searchRequest.getStatus();
                String jsonResponse = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
                var rootNode = objectMapper.readTree(jsonResponse);
                if (rootNode.isArray()) {
                    for (var node : rootNode) {
                        Pet pet = mapJsonNodeToPet(node);
                        if (matchesSearch(pet, searchRequest)) results.add(pet);
                    }
                }
            } catch (Exception ex) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to search pets: " + ex.getMessage());
            }
        } else {
            ArrayNode itemsNode = entityService.getItems("pet", ENTITY_VERSION).join();
            for (var node : itemsNode) {
                if (node.isObject()) {
                    ObjectNode objNode = (ObjectNode) node;
                    Pet pet = convertObjectNodeToPet(objNode);
                    if (matchesSearch(pet, searchRequest)) results.add(pet);
                }
            }
        }

        return ResponseEntity.ok(new SearchPetsResponse(results));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) {
        logger.info("Received getPetById request for technicalId {}", id);
        ObjectNode node = entityService.getItem("pet", ENTITY_VERSION, id).join();
        if (node == null || !node.hasNonNull("technicalId")) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Pet with technicalId " + id + " not found");
        }
        Pet pet = convertObjectNodeToPet(node);
        return ResponseEntity.ok(pet);
    }

    private ObjectNode processPet(ObjectNode entity) {
        logger.info("Processing pet entity in workflow before persistence: {}", entity);

        if (!entity.hasNonNull("description") || entity.get("description").asText().isBlank()) {
            entity.put("description", "No description provided.");
        }

        CompletableFuture.runAsync(() -> {
            String technicalId = entity.hasNonNull("technicalId") ? entity.get("technicalId").asText() : "<unknown>";
            logger.info("Workflow triggered for pet technicalId={} at {}", technicalId, Instant.now());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
            logger.info("Workflow completed for pet technicalId={}", technicalId);
        });

        return entity;
    }

    private Pet convertObjectNodeToPet(ObjectNode node) {
        String technicalId = node.hasNonNull("technicalId") ? node.get("technicalId").asText() : null;
        String name = node.hasNonNull("name") ? node.get("name").asText() : "";
        String category = node.hasNonNull("category") ? node.get("category").asText() : "";
        String status = node.hasNonNull("status") ? node.get("status").asText() : "";
        Integer age = node.hasNonNull("age") && !node.get("age").isNull() ? node.get("age").asInt() : null;
        String breed = node.hasNonNull("breed") ? node.get("breed").asText() : "";
        String description = node.hasNonNull("description") ? node.get("description").asText() : "";
        return new Pet(technicalId, name, category, status, age, breed, description);
    }

    private Pet mapJsonNodeToPet(JsonNode node) {
        String id = node.hasNonNull("id") ? node.get("id").asText() : UUID.randomUUID().toString();
        String name = node.hasNonNull("name") ? node.get("name").asText() : "";
        String category = node.hasNonNull("category") && node.get("category").hasNonNull("name")
                ? node.get("category").get("name").asText() : "";
        String status = node.hasNonNull("status") ? node.get("status").asText() : "";
        return new Pet(id, name, category, status, null, null, null);
    }

    private boolean matchesSearch(Pet pet, SearchPetsRequest req) {
        if (req.getCategory() != null && !req.getCategory().equalsIgnoreCase(pet.getCategory())) return false;
        if (req.getName() != null && !pet.getName().toLowerCase().contains(req.getName().toLowerCase())) return false;
        if (req.getStatus() != null && !req.getStatus().equalsIgnoreCase(pet.getStatus())) return false;
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetRequest {
        private String id;
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 50)
        private String category;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @NotNull
        private Integer age;
        @NotBlank
        @Size(max = 50)
        private String breed;
        @Size(max = 250)
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchPetsRequest {
        @Size(max = 50)
        private String category;
        @Size(max = 20)
        private String status;
        @Size(max = 100)
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private String id;
        private String name;
        private String category;
        private String status;
        private Integer age;
        private String breed;
        private String description;

        public Pet(String id, String name, String category, String status, Integer age, String breed, String description) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.status = status;
            this.age = age;
            this.breed = breed;
            this.description = description;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddUpdatePetResponse {
        private boolean success;
        private Pet pet;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchPetsResponse {
        private List<Pet> results;
    }
}