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
    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";
    private static final String ENTITY_NAME = "Pet";

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
        List<Pet> pets =  objectMapper.convertValue(responseJson, objectMapper.getTypeFactory().constructCollectionType(List.class, Pet.class));
        // Set description manually because original mapping expects tags
        for (int i = 0; i < responseJson.size(); i++) {
            JsonNode node = responseJson.get(i);
            Pet pet = pets.get(i);
            if (node.has("tags") && node.get("tags").isArray() && node.get("tags").size() > 0) {
                pet.setDescription(node.get("tags").get(0).path("name").asText("No description"));
            } else {
                pet.setDescription("No description");
            }
            if (pet.getType() == null || pet.getType().isBlank()) {
                pet.setType(node.path("category").path("name").asText("unknown"));
            }
            if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                pet.setStatus(node.path("status").asText("unknown"));
            }
        }
        // Convert to ObjectNodes for entityService
        List<ObjectNode> petNodes = pets.stream()
                .map(p -> objectMapper.valueToTree(p))
                .collect(Collectors.toList());
        CompletableFuture<List<UUID>> idsFuture = entityService.addItems(ENTITY_NAME, ENTITY_VERSION, petNodes);
        idsFuture.get(); // wait completion
        FetchPetsResponse resp = new FetchPetsResponse();
        resp.setPets(pets);
        logger.info("Stored {} pets", pets.size());
        return ResponseEntity.ok(resp);
    }

    @GetMapping
    public ResponseEntity<GetPetsResponse> getPets() throws ExecutionException, InterruptedException {
        logger.info("Retrieving stored pets");
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(ENTITY_NAME, ENTITY_VERSION);
        ArrayNode items = itemsFuture.get();
        List<Pet> pets = items.findValuesAsText("technicalId").isEmpty() ? List.of() :
                items.findValuesAsText("technicalId").stream().map(tid -> {
                    try {
                        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ENTITY_NAME, ENTITY_VERSION, UUID.fromString(tid));
                        ObjectNode obj = itemFuture.get();
                        return objectMapper.treeToValue(obj, Pet.class);
                    } catch (Exception e) {
                        logger.error("Error fetching pet by technicalId {}", tid, e);
                        return null;
                    }
                }).filter(p -> p != null).collect(Collectors.toList());
        // The above is inefficient, better to just map items directly
        pets = items.findValuesAsText("technicalId").isEmpty() ? List.of() :
                items.findValues(null).stream()
                        .map(node -> {
                            try {
                                return objectMapper.treeToValue(node, Pet.class);
                            } catch (Exception e) {
                                logger.error("Error mapping pet from node", e);
                                return null;
                            }
                        }).filter(p -> p != null).collect(Collectors.toList());

        GetPetsResponse resp = new GetPetsResponse();
        resp.setPets(pets);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptPetResponse> adoptPet(@RequestBody @Valid AdoptPetRequest request) throws ExecutionException, InterruptedException {
        logger.info("Adopt request for petId={}", request.getPetId());
        // Query by condition technicalId = UUID from integer id string
        // But original Pet id is Integer, here we must find the Pet by a field 'id' equal to request.petId
        Condition cond = Condition.of("$.id", "EQUALS", request.getPetId());
        SearchConditionRequest searchCond = SearchConditionRequest.group("AND", cond);
        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(ENTITY_NAME, ENTITY_VERSION, searchCond);
        ArrayNode petsArray = filteredItemsFuture.get();
        if (petsArray == null || petsArray.size() == 0) {
            logger.error("Pet {} not found", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        ObjectNode petNode = (ObjectNode) petsArray.get(0);
        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
        String technicalIdStr = petNode.path("technicalId").asText();
        if ("adopted".equalsIgnoreCase(pet.getStatus())) {
            return ResponseEntity.ok(new AdoptPetResponse("Pet already adopted", pet));
        }
        pet.setStatus("adopted");
        UUID technicalId = UUID.fromString(technicalIdStr);
        CompletableFuture<UUID> updatedItemId = entityService.updateItem(ENTITY_NAME, ENTITY_VERSION, technicalId, objectMapper.valueToTree(pet));
        updatedItemId.get();
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