package com.java_template.entity;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/entity/pets")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;

    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";
    private static final String ENTITY_NAME = "Pet";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/fetch")
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody @Valid FetchPetsRequest request) throws ExecutionException, InterruptedException {
        String statusFilter = request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus() : "available";
        logger.info("Fetching pets with status={}", statusFilter);

        // Fetch from external API
        var responseJson = restTemplate.getForObject(PETSTORE_API_URL, ObjectNode[].class, statusFilter);
        if (responseJson == null) {
            logger.error("Invalid response from Petstore API");
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Invalid response from external API");
        }

        // Map and prepare Pet objects
        List<Pet> pets = java.util.Arrays.stream(responseJson).map(node -> {
            Pet pet = new Pet();
            pet.setName(node.path("name").asText(""));
            pet.setType(node.path("category").path("name").asText("unknown"));
            pet.setStatus(node.path("status").asText("unknown"));
            if (node.has("tags") && node.get("tags").isArray() && node.get("tags").size() > 0) {
                pet.setDescription(node.get("tags").get(0).path("name").asText("No description"));
            } else {
                pet.setDescription("No description");
            }
            return pet;
        }).collect(Collectors.toList());

        // Save pets via entityService
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                ENTITY_NAME,
                ENTITY_VERSION,
                pets
        );
        List<UUID> ids = idsFuture.get();

        // Assign technicalIds to pets
        for (int i = 0; i < ids.size() && i < pets.size(); i++) {
            pets.get(i).setTechnicalId(ids.get(i));
        }

        FetchPetsResponse resp = new FetchPetsResponse();
        resp.setPets(pets);
        logger.info("Stored {} pets", pets.size());
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<GetPetsResponse> getPets() throws ExecutionException, InterruptedException {
        logger.info("Retrieving all pets via EntityService");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        List<Pet> pets = items.findValuesAsText("technicalId").isEmpty() ? List.of() :
            java.util.stream.StreamSupport.stream(items.spliterator(), false)
            .map(node -> {
                Pet pet = new Pet();
                pet.setTechnicalId(UUID.fromString(node.path("technicalId").asText()));
                pet.setName(node.path("name").asText(""));
                pet.setType(node.path("type").asText(""));
                pet.setStatus(node.path("status").asText(""));
                pet.setDescription(node.path("description").asText(""));
                return pet;
            }).collect(Collectors.toList());

        GetPetsResponse resp = new GetPetsResponse();
        resp.setPets(pets);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptPetResponse> adoptPet(@RequestBody @Valid AdoptPetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Adopt request for petId={}", request.getPetId());
        UUID technicalId = request.getPetId();
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId);
        ObjectNode petNode = itemFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            logger.error("Pet {} not found", technicalId);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
        }

        String currentStatus = petNode.path("status").asText("");
        if ("adopted".equalsIgnoreCase(currentStatus)) {
            Pet pet = toPet(petNode);
            return ResponseEntity.ok(new AdoptPetResponse("Pet already adopted", pet));
        }

        petNode.put("status", "adopted");
        Pet updatedPet = toPet(petNode);
        CompletableFuture<UUID> updatedItemId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, updatedPet);
        updatedItemId.get();

        logger.info("Pet {} adopted", technicalId);
        return ResponseEntity.ok(new AdoptPetResponse("Pet adopted successfully", updatedPet));
    }

    private Pet toPet(ObjectNode node) {
        Pet pet = new Pet();
        pet.setTechnicalId(UUID.fromString(node.path("technicalId").asText()));
        pet.setName(node.path("name").asText(""));
        pet.setType(node.path("type").asText(""));
        pet.setStatus(node.path("status").asText(""));
        pet.setDescription(node.path("description").asText(""));
        return pet;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ErrorResponse error = new ErrorResponse(org.springframework.http.HttpStatus.BAD_REQUEST.toString(), msg);
        return new ResponseEntity<>(error, org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchPetsRequest {
        @Size(max = 50)
        private String type;
        @Size(max = 50)
        private String status;
    }

    @Data
    public static class FetchPetsResponse {
        private List<Pet> pets;
    }

    @Data
    public static class GetPetsResponse {
        private List<Pet> pets;
    }

    @Data
    public static class AdoptPetRequest {
        @NotNull
        private UUID petId;
    }

    @Data
    @AllArgsConstructor
    public static class AdoptPetResponse {
        private String message;
        private Pet pet;
    }

    @Data
    @NoArgsConstructor
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private String name;
        private String type;
        private String status;
        private String description;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}