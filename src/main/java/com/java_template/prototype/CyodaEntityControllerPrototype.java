package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/entity/pets")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final EntityService entityService;
    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";
    private static final String ENTITY_NAME = "Pet";

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<FetchPetsResponse>> fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        String statusFilter = request.getStatus() != null && !request.getStatus().isBlank()
            ? request.getStatus() : "available";
        logger.info("Fetching pets with status={}", statusFilter);

        return CompletableFuture.supplyAsync(() -> {
            JsonNode responseJson = restTemplate.getForObject(PETSTORE_API_URL, JsonNode.class, statusFilter);
            if (responseJson == null || !responseJson.isArray()) {
                logger.error("Invalid response from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }
            List<Pet> pets = null;
            try {
                pets = parsePetsFromJson(responseJson);
            } catch (Exception ex) {
                logger.error("Error parsing pets from JSON", ex);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to parse pet data");
            }
            // Add pets to external entityService
            return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, pets)
                .thenApply(ids -> {
                    logger.info("Stored {} pets via entityService", pets.size());
                    FetchPetsResponse resp = new FetchPetsResponse();
                    resp.setPets(pets);
                    return ResponseEntity.ok(resp);
                }).join();
        });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<GetPetsResponse>> getPets() {
        logger.info("Retrieving pets from entityService");
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
            .thenApply(arrayNode -> {
                List<Pet> pets = arrayNode.findValuesAsText("technicalId").isEmpty() 
                    ? List.of() 
                    : arrayNode.findValuesAsText("technicalId").stream().map(technicalId -> {
                        ObjectNode node = (ObjectNode) arrayNode.stream()
                            .filter(n -> technicalId.equals(((ObjectNode) n).get("technicalId").asText()))
                            .findFirst().orElse(null);
                        if (node == null) return null;
                        return nodeToPet(node);
                    }).filter(p -> p != null).collect(Collectors.toList());
                GetPetsResponse resp = new GetPetsResponse();
                resp.setPets(pets);
                logger.info("Retrieved {} pets", pets.size());
                return ResponseEntity.ok(resp);
            });
    }

    @PostMapping("/adopt")
    public CompletableFuture<ResponseEntity<AdoptPetResponse>> adoptPet(@RequestBody @Valid AdoptPetRequest request) {
        logger.info("Adopt request for petId={}", request.getPetId());

        UUID petTechnicalId = null;
        // Search pet by id using condition
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.id", "EQUALS", request.getPetId()));
        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
            .thenCompose(arrayNode -> {
                if (arrayNode == null || arrayNode.isEmpty()) {
                    logger.error("Pet {} not found", request.getPetId());
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                }
                ObjectNode petNode = (ObjectNode) arrayNode.get(0);
                petTechnicalId = UUID.fromString(petNode.get("technicalId").asText());
                Pet pet = nodeToPet(petNode);
                if ("adopted".equalsIgnoreCase(pet.getStatus())) {
                    return CompletableFuture.completedFuture(ResponseEntity.ok(
                        new AdoptPetResponse("Pet already adopted", pet)));
                }
                pet.setStatus("adopted");
                return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, petTechnicalId, pet)
                    .thenApply(updatedId -> {
                        logger.info("Pet {} adopted", petTechnicalId);
                        return ResponseEntity.ok(new AdoptPetResponse("Pet adopted successfully", pet));
                    });
            });
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

    private List<Pet> parsePetsFromJson(JsonNode arrayNode) {
        return arrayNode.findValuesAsText("id").isEmpty() 
            ? List.of()
            : arrayNode.findValuesAsText("id").stream().map(idText -> {
                for (JsonNode node : arrayNode) {
                    if (node.path("id").asText().equals(idText)) {
                        Pet pet = new Pet();
                        pet.setId(node.path("id").asInt());
                        pet.setName(node.path("name").asText(""));
                        pet.setType(node.path("category").path("name").asText("unknown"));
                        pet.setStatus(node.path("status").asText("unknown"));
                        if (node.has("tags") && node.get("tags").isArray() && node.get("tags").size() > 0) {
                            pet.setDescription(node.get("tags").get(0).path("name").asText("No description"));
                        } else {
                            pet.setDescription("No description");
                        }
                        return pet;
                    }
                }
                return null;
            }).filter(pet -> pet != null).collect(Collectors.toList());
    }

    private Pet nodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        if (node.has("id") && node.get("id").canConvertToInt()) {
            pet.setId(node.get("id").asInt());
        }
        if (node.has("name")) {
            pet.setName(node.get("name").asText());
        }
        if (node.has("type")) {
            pet.setType(node.get("type").asText());
        } else if (node.has("category")) {
            pet.setType(node.get("category").asText());
        }
        if (node.has("status")) {
            pet.setStatus(node.get("status").asText());
        }
        if (node.has("description")) {
            pet.setDescription(node.get("description").asText());
        }
        return pet;
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