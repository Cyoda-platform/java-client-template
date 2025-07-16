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

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Validated
@RestController
@RequestMapping(path = "/entity/pets")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);
    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME = "Pet";
    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<FetchPetsResponse>> fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        String statusFilter = request.getStatus() != null && !request.getStatus().isBlank()
                ? request.getStatus() : "available";
        logger.info("Fetching pets with status={}", statusFilter);

        // Fetch from external API
        JsonNode responseJson = restTemplate.getForObject(PETSTORE_API_URL, JsonNode.class, statusFilter);
        if (responseJson == null || !responseJson.isArray()) {
            logger.error("Invalid response from Petstore API");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
        }

        // Convert JsonNode array to List<Pet>
        List<Pet> pets = 
                ((ArrayNode) responseJson).findValuesAsText("id").isEmpty() ? List.of() :
                ((ArrayNode) responseJson).findValues("id").stream()
                .map(node -> (JsonNode) node)
                .collect(Collectors.toList()).isEmpty() ? List.of() : null;

        // But better to parse pets manually:
        List<Pet> petList = 
            ((ArrayNode) responseJson).findValues("id").isEmpty() ?
            List.of() :
            ((ArrayNode) responseJson).stream().map(node -> {
                Pet pet = new Pet();
                pet.setId(null); // id ignored, using technicalId later
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

        // Save pets via entityService.addItems
        return entityService.addItems(ENTITY_NAME, ENTITY_VERSION, petList)
                .thenApply(ids -> {
                    logger.info("Stored {} pets", ids.size());
                    FetchPetsResponse resp = new FetchPetsResponse();
                    resp.setPets(petList);
                    return ResponseEntity.ok(resp);
                });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<GetPetsResponse>> getPets() {
        logger.info("Retrieving pets from external service");
        return entityService.getItems(ENTITY_NAME, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Pet> pets = arrayNode.findValuesAsText("technicalId").isEmpty() ? List.of() :
                            arrayNode.stream().map(node -> {
                                Pet pet = new Pet();
                                pet.setTechnicalId(UUID.fromString(node.path("technicalId").asText()));
                                pet.setId(null); // local id ignored
                                pet.setName(node.path("name").asText(""));
                                pet.setType(node.path("type").asText("unknown"));
                                pet.setStatus(node.path("status").asText("unknown"));
                                pet.setDescription(node.path("description").asText("No description"));
                                return pet;
                            }).collect(Collectors.toList());
                    GetPetsResponse resp = new GetPetsResponse();
                    resp.setPets(pets);
                    logger.info("Retrieved {} pets", pets.size());
                    return ResponseEntity.ok(resp);
                });
    }

    @PostMapping("/adopt")
    public CompletableFuture<ResponseEntity<AdoptPetResponse>> adoptPet(@RequestBody @Valid AdoptPetRequest request) {
        UUID technicalId = request.getTechnicalId();
        logger.info("Adopt request for technicalId={}", technicalId);
        return entityService.getItem(ENTITY_NAME, ENTITY_VERSION, technicalId)
                .thenCompose(itemNode -> {
                    if (itemNode == null || itemNode.isEmpty()) {
                        logger.error("Pet {} not found", technicalId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                    }
                    String status = itemNode.path("status").asText("unknown");
                    if ("adopted".equalsIgnoreCase(status)) {
                        Pet pet = objectNodeToPet(itemNode);
                        return CompletableFuture.completedFuture(
                                ResponseEntity.ok(new AdoptPetResponse("Pet already adopted", pet)));
                    }
                    // Update status to adopted
                    Pet petToUpdate = objectNodeToPet(itemNode);
                    petToUpdate.setStatus("adopted");
                    return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, petToUpdate)
                            .thenApply(id -> ResponseEntity.ok(new AdoptPetResponse("Pet adopted successfully", petToUpdate)));
                });
    }

    private Pet objectNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        if (node.has("technicalId") && !node.get("technicalId").isNull()) {
            pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
        }
        pet.setName(node.path("name").asText(""));
        pet.setType(node.path("type").asText("unknown"));
        pet.setStatus(node.path("status").asText("unknown"));
        pet.setDescription(node.path("description").asText("No description"));
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
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.toString(), msg);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
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
        private UUID technicalId;
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
        private UUID technicalId; // The unique technical id from entityService
        private Integer id; // local id ignored
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