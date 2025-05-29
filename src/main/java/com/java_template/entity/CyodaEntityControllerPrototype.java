package com.java_template.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Slf4j
@RestController
@RequestMapping("/cyoda-pets")
@Validated
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private static final String ENTITY_NAME = "pet";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostConstruct
    public void init() {
        logger.info("CyodaEntityControllerPrototype initialized");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchPetsRequest {
        @NotBlank
        private String status;    // required for Petstore API query
        private String type;      // optional filter
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePetRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotBlank
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStatusRequest {
        @NotBlank
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        @JsonIgnore
        private UUID technicalId;
        private String name;
        private String type;
        private String status;
        private Integer age;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
        private Integer count;
        private UUID id;
        private String oldStatus;
        private String newStatus;
    }

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse fetchPets(@RequestBody @Valid FetchPetsRequest request) throws ExecutionException, InterruptedException {
        logger.info("Received fetchPets request with filters status={} type={}", request.getStatus(), request.getType());
        String url = "https://petstore.swagger.io/v2/pet/findByStatus?status=" + request.getStatus();
        JsonNode petstoreResponse;
        try {
            String responseStr = restTemplate.getForObject(url, String.class);
            petstoreResponse = objectMapper.readTree(responseStr);
        } catch (Exception e) {
            logger.error("Error fetching data from Petstore API", e);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Failed to fetch data from Petstore API");
        }
        List<Pet> petsToStore = new ArrayList<>();
        if (petstoreResponse.isArray()) {
            for (JsonNode petNode : petstoreResponse) {
                String petType = petNode.path("category").path("name").asText(null);
                if (request.getType() != null && !request.getType().isEmpty() &&
                        (petType == null || !petType.equalsIgnoreCase(request.getType()))) {
                    continue;
                }
                String name = petNode.path("name").asText("Unnamed");
                String status = request.getStatus();
                Integer age = null;
                String description = petNode.path("description").asText(null);
                Pet pet = new Pet(null, name, petType, status, age, description);
                petsToStore.add(pet);
            }
        }
        if (petsToStore.isEmpty()) {
            logger.info("No pets to store after filtering");
            return new MessageResponse("No pets matched the filters", 0, null, null, null);
        }
        // Add pets to external service
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(ENTITY_NAME, ENTITY_VERSION, petsToStore);
        List<UUID> storedIds = idsFuture.get();
        logger.info("Stored {} pets fetched from Petstore API", storedIds.size());
        return new MessageResponse("Pets data fetched and stored successfully", storedIds.size(), null, null, null);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse createPet(@RequestBody @Valid CreatePetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Creating new pet: name={}, type={}, status={}", request.getName(), request.getType(), request.getStatus());
        Pet pet = new Pet(null, request.getName(), request.getType(), request.getStatus(), request.getAge(), request.getDescription());
        CompletableFuture<UUID> idFuture = entityService.addItem(ENTITY_NAME, ENTITY_VERSION, pet);
        UUID newId = idFuture.get();
        logger.info("Pet created with technicalId={}", newId);
        return new MessageResponse("Pet created successfully", null, newId, null, null);
    }

    @PostMapping(value = "/{id}/status", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MessageResponse updatePetStatus(@PathVariable("id") UUID id, @RequestBody @Valid UpdateStatusRequest request) throws ExecutionException, InterruptedException {
        logger.info("Updating pet(technicalId={}) status to {}", id, request.getStatus());
        // Retrieve existing pet
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode petNode = itemFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        // Convert ObjectNode to Pet
        Pet pet = objectMapper.convertValue(petNode, Pet.class);
        String oldStatus = pet.getStatus();
        pet.setStatus(request.getStatus());
        // Update pet
        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, id, pet);
        UUID updatedId = updatedIdFuture.get();
        // Trigger async workflow
        CompletableFuture.runAsync(() -> {
            logger.info("Triggered workflow for pet(technicalId={}) status change from {} to {}", id, oldStatus, request.getStatus());
            // TODO: implement actual workflow logic
        });
        return new MessageResponse("Pet status updated", null, updatedId, oldStatus, request.getStatus());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> getPets(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "type", required = false) String type
    ) throws ExecutionException, InterruptedException {
        logger.info("Fetching pets list with filters status={} type={}", status, type);
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode itemsNode = itemsFuture.get();
        if (itemsNode == null || itemsNode.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pet> pets = new ArrayList<>();
        for (JsonNode node : itemsNode) {
            Pet pet = objectMapper.convertValue(node, Pet.class);
            pets.add(pet);
        }
        List<Pet> filtered = pets.stream()
                .filter(pet -> {
                    if (status != null && !status.isEmpty() && !status.equalsIgnoreCase(pet.getStatus())) {
                        return false;
                    }
                    if (type != null && !type.isEmpty() && !type.equalsIgnoreCase(pet.getType())) {
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        logger.info("Returning {} pets after filtering", filtered.size());
        return filtered;
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") UUID id) throws ExecutionException, InterruptedException {
        logger.info("Fetching pet details for technicalId={}", id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, id);
        ObjectNode petNode = itemFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }
        return objectMapper.convertValue(petNode, Pet.class);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("ResponseStatusException: {}", ex.getReason());
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("status", ex.getStatusCode().value());
        errorMap.put("error", ex.getReason());
        return errorMap;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception ex) {
        logger.error("Unhandled exception", ex);
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("status", 500);
        errorMap.put("error", "Internal server error");
        return errorMap;
    }
}