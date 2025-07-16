package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final String ENTITY_NAME = "Pet";

    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    @PostMapping("/fetch")
    public CompletableFuture<ResponseEntity<FetchPetsResponse>> fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        String statusFilter = request.getStatus() != null && !request.getStatus().isBlank()
            ? request.getStatus() : "available";
        logger.info("Fetching pets with status={}", statusFilter);

        // Fetch pets from external API
        JsonNode responseJson = restTemplate.getForObject(PETSTORE_API_URL, JsonNode.class, statusFilter);
        if (responseJson == null || !responseJson.isArray()) {
            logger.error("Invalid response from Petstore API");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
        }

        // Map JsonNode to Pet entities
        List<Pet> pets = 
            objectMapper.convertValue(responseJson, objectMapper.getTypeFactory().constructCollectionType(List.class, ObjectNode.class))
            .stream()
            .map(node -> {
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

        // Store pets via entityService
        return entityService.addItems(
                ENTITY_NAME,
                ENTITY_VERSION,
                pets
            ).thenApply(ids -> {
                logger.info("Stored {} pets", pets.size());
                FetchPetsResponse resp = new FetchPetsResponse();
                resp.setPets(pets);
                return ResponseEntity.ok(resp);
            });
    }

    @GetMapping
    public CompletableFuture<ResponseEntity<GetPetsResponse>> getPets() {
        logger.info("Retrieving pets");
        return entityService.getItems(
                ENTITY_NAME,
                ENTITY_VERSION
            ).thenApply(arrayNode -> {
                List<Pet> pets = arrayNode == null || arrayNode.isEmpty()
                    ? List.of()
                    : arrayNode.stream().map(node -> objectMapper.convertValue(node, Pet.class)).collect(Collectors.toList());
                GetPetsResponse resp = new GetPetsResponse();
                resp.setPets(pets);
                logger.info("Retrieved {} pets", pets.size());
                return ResponseEntity.ok(resp);
            });
    }

    @PostMapping("/adopt")
    public CompletableFuture<ResponseEntity<AdoptPetResponse>> adoptPet(@RequestBody @Valid AdoptPetRequest request) {
        logger.info("Adopt request for petId={}", request.getPetId());

        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$.id", "EQUALS", request.getPetId())
        );

        return entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, condition)
            .thenCompose(arrayNode -> {
                if (arrayNode == null || arrayNode.isEmpty()) {
                    logger.error("Pet {} not found", request.getPetId());
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                }
                ObjectNode petNode = (ObjectNode) arrayNode.get(0);
                Pet pet = objectMapper.convertValue(petNode, Pet.class);
                if ("adopted".equalsIgnoreCase(pet.getStatus())) {
                    return CompletableFuture.completedFuture(ResponseEntity.ok(new AdoptPetResponse("Pet already adopted", pet)));
                }
                pet.setStatus("adopted");
                UUID petTechnicalId = UUID.fromString(petNode.get("technicalId").asText());
                return entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, petTechnicalId, pet)
                    .thenApply(id -> {
                        logger.info("Pet {} adopted", pet.getId());
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
            .map(fe -> fe.getField()+": "+fe.getDefaultMessage())
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
