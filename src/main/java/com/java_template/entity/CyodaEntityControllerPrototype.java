package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile List<Pet> lastFetchedPets = Collections.emptyList();

    private final EntityService entityService;
    private static final String ENTITY_NAME = "pet";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        @JsonIgnore
        private UUID technicalId; // replaced id with technicalId from entityService
        private String name;
        private String type;
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetFetchRequest {
        @Size(max = 30)
        private String type;
        @Pattern(regexp = "available|pending|sold", message = "status must be one of: available, pending, sold")
        private String status;
        @Size(max = 50)
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetAddRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 30)
        private String type;
        @Pattern(regexp = "available|pending|sold", message = "status must be one of: available, pending, sold")
        private String status;
        @Min(value = 0, message = "age must be non-negative")
        private Integer age;
        @Size(max = 255)
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetUpdateRequest {
        @Size(max = 100)
        private String name;
        @Size(max = 30)
        private String type;
        @Pattern(regexp = "available|pending|sold", message = "status must be one of: available, pending, sold")
        private String status;
        @Min(value = 0, message = "age must be non-negative")
        private Integer age;
        @Size(max = 255)
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetAddResponse {
        private String message;
        private UUID technicalId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<Pet>>> fetchPets(@RequestBody @Valid PetFetchRequest fetchRequest) throws Exception {
        logger.info("Received fetch request: {}", fetchRequest);
        String statusParam = StringUtils.hasText(fetchRequest.getStatus()) ? fetchRequest.getStatus() : "available";
        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + statusParam;
        logger.info("Calling external Petstore API: {}", url);
        String responseJson = restTemplate.getForObject(url, String.class);
        if (responseJson == null) {
            logger.error("Empty response from Petstore API");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned empty response");
        }
        JsonNode rootNode = objectMapper.readTree(responseJson);
        if (!rootNode.isArray()) {
            logger.error("Unexpected Petstore API response format, expected array");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned unexpected data");
        }
        List<Pet> resultPets = new ArrayList<>();
        for (JsonNode petNode : rootNode) {
            Long externalId = petNode.has("id") ? petNode.get("id").asLong() : null;
            String name = petNode.has("name") ? petNode.get("name").asText() : null;
            String type = null;
            if (petNode.has("category") && petNode.get("category").has("name")) {
                type = petNode.get("category").get("name").asText();
            }
            String status = petNode.has("status") ? petNode.get("status").asText() : null;
            Integer age = null; // Not provided by external API
            String description = null; // Not provided by external API

            if (StringUtils.hasText(fetchRequest.getType()) && (type == null || !type.equalsIgnoreCase(fetchRequest.getType()))) {
                continue;
            }
            if (StringUtils.hasText(fetchRequest.getName()) && (name == null || !name.toLowerCase().contains(fetchRequest.getName().toLowerCase()))) {
                continue;
            }
            // technicalId is null here because these are fetched from external API only
            resultPets.add(new Pet(null, name, type, status, age, description));
        }
        this.lastFetchedPets = Collections.unmodifiableList(resultPets);
        Map<String, List<Pet>> response = Map.of("pets", resultPets);
        logger.info("Returning {} pets after filtering", resultPets.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, List<Pet>>> getPets() {
        logger.info("Returning last fetched pet list, count: {}", lastFetchedPets.size());
        return ResponseEntity.ok(Map.of("pets", lastFetchedPets));
    }

    @PostMapping(value = "/add", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetAddResponse> addPet(@RequestBody @Valid PetAddRequest addRequest) throws Exception {
        logger.info("Adding new pet: {}", addRequest);
        Pet newPet = new Pet(null, addRequest.getName(), addRequest.getType(),
                addRequest.getStatus() != null ? addRequest.getStatus() : "available",
                addRequest.getAge(), addRequest.getDescription());

        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, newPet);
        UUID technicalId = idFuture.get();

        logger.info("Pet added with technicalId {}", technicalId);
        return ResponseEntity.ok(new PetAddResponse("Pet added successfully", technicalId));
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Pet> getPetById(@PathVariable("id") UUID id) throws Exception {
        logger.info("Fetching pet by technicalId {}", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode itemNode = itemFuture.get();
        if (itemNode == null || itemNode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with technicalId " + id);
        }
        Pet pet = objectMapper.treeToValue(itemNode, Pet.class);
        pet.setTechnicalId(id);
        return ResponseEntity.ok(pet);
    }

    @PostMapping(value = "/update/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MessageResponse> updatePet(@PathVariable("id") UUID id,
                                                     @RequestBody @Valid PetUpdateRequest updateRequest) throws Exception {
        logger.info("Updating pet technicalId {} with data: {}", id, updateRequest);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode existingNode = itemFuture.get();
        if (existingNode == null || existingNode.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found with technicalId " + id);
        }
        Pet pet = objectMapper.treeToValue(existingNode, Pet.class);
        pet.setTechnicalId(id);

        if (StringUtils.hasText(updateRequest.getName())) pet.setName(updateRequest.getName());
        if (StringUtils.hasText(updateRequest.getType())) pet.setType(updateRequest.getType());
        if (StringUtils.hasText(updateRequest.getStatus())) pet.setStatus(updateRequest.getStatus());
        if (updateRequest.getAge() != null) pet.setAge(updateRequest.getAge());
        if (updateRequest.getDescription() != null) pet.setDescription(updateRequest.getDescription());

        CompletableFuture<UUID> updatedItemId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, pet);
        UUID updatedId = updatedItemId.get();

        logger.info("Pet technicalId {} updated", updatedId);
        return ResponseEntity.ok(new MessageResponse("Pet updated successfully"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handled ResponseStatusException: {}", ex.getReason(), ex);
        Map<String, String> error = Map.of(
                "status", ex.getStatusCode().toString(),
                "error", ex.getReason()
        );
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, String> error = Map.of(
                "status", String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()),
                "error", "Internal server error"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}