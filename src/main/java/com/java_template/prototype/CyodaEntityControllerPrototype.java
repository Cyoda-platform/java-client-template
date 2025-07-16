package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME = "Pet";

    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    @PostMapping("/fetch")
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody @Valid FetchPetsRequest request) throws ExecutionException, InterruptedException {
        String statusFilter = request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus() : "available";
        logger.info("Fetching pets with status={}", statusFilter);
        JsonNode responseJson = restTemplate.getForObject(PETSTORE_API_URL, JsonNode.class, statusFilter);
        if (responseJson == null || !responseJson.isArray()) {
            logger.error("Invalid response from Petstore API");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
        }
        List<Pet> pets = extractPets(responseJson);
        // Use entityService.addItems to store pets externally
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(
                ENTITY_NAME,
                ENTITY_VERSION,
                pets
        );
        idsFuture.get(); // wait for completion, propagate exception if any
        FetchPetsResponse resp = new FetchPetsResponse();
        resp.setPets(pets);
        logger.info("Stored {} pets externally", pets.size());
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<GetPetsResponse> getPets() throws ExecutionException, InterruptedException {
        logger.info("Retrieving stored pets");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                ENTITY_NAME,
                ENTITY_VERSION
        );
        ArrayNode items = itemsFuture.get();
        List<Pet> pets = items.findValuesAsText("technicalId").isEmpty() ? List.of() :
                items.findValuesAsText("technicalId").stream().map(tid -> {
                    // map ObjectNode to Pet
                    ObjectNode obj = (ObjectNode) items.stream()
                            .filter(n -> n.path("technicalId").asText().equals(tid))
                            .findFirst()
                            .orElse(null);
                    if (obj == null) return null;
                    try {
                        Pet pet = objectMapper.treeToValue(obj, Pet.class);
                        return pet;
                    } catch (Exception e) {
                        logger.error("Failed to convert ObjectNode to Pet", e);
                        return null;
                    }
                }).collect(Collectors.toList());
        // better way:
        List<Pet> petsList = items.findValuesAsText("technicalId").isEmpty()
                ? List.of()
                : items.stream()
                .map(n -> {
                    try {
                        Pet pet = objectMapper.treeToValue(n, Pet.class);
                        return pet;
                    } catch (Exception e) {
                        logger.error("Failed to convert ObjectNode to Pet", e);
                        return null;
                    }
                })
                .filter(p -> p != null)
                .collect(Collectors.toList());
        GetPetsResponse resp = new GetPetsResponse();
        resp.setPets(petsList);
        logger.info("Retrieved {} pets", petsList.size());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptPetResponse> adoptPet(@RequestBody @Valid AdoptPetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Adopt request for petId={}", request.getPetId());
        // Find pet by technicalId equals request.getPetId().toString()
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", request.getPetId().toString())
        );
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                ENTITY_NAME,
                ENTITY_VERSION,
                condition
        );
        ArrayNode filteredItems = filteredItemsFuture.get();
        if (filteredItems.isEmpty()) {
            logger.error("Pet {} not found", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        ObjectNode petNode = (ObjectNode) filteredItems.get(0);
        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
        if ("adopted".equalsIgnoreCase(pet.getStatus())) {
            return ResponseEntity.ok(new AdoptPetResponse("Pet already adopted", pet));
        }
        pet.setStatus("adopted");
        CompletableFuture<UUID> updatedIdFuture = entityService.updateItem(
                ENTITY_NAME,
                ENTITY_VERSION,
                UUID.fromString(petNode.get("technicalId").asText()),
                pet
        );
        updatedIdFuture.get();
        logger.info("Pet {} adopted", pet.getId());
        return ResponseEntity.ok(new AdoptPetResponse("Pet adopted successfully", pet));
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
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.toString(), msg);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    private List<Pet> extractPets(JsonNode responseJson) {
        return responseJson.findValues("id").isEmpty() ? List.of() :
                responseJson.findValues("id").stream().map(node -> {
                    // Actually map node to Pet
                    // but better to iterate over responseJson elements
                    return null;
                }).collect(Collectors.toList());
    }

    private List<Pet> extractPets(JsonNode arrayNode) {
        return arrayNode.isArray() ? 
            objectMapper.convertValue(arrayNode, objectMapper.getTypeFactory().constructCollectionType(List.class, Pet.class))
            : List.of();
    }

    @Data
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
        private Integer petId;
    }

    @Data
    public static class AdoptPetResponse {
        private String message;
        private Pet pet;
        public AdoptPetResponse(String message, Pet pet) {
            this.message = message;
            this.pet = pet;
        }
    }

    @Data
    public static class Pet {
        @com.fasterxml.jackson.annotation.JsonIgnore
        private UUID technicalId;
        private Integer id;
        private String name;
        private String type;
        private String status;
        private String description;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}