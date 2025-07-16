package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping(path = "/prototype/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Integer, Pet> petStorage = new ConcurrentHashMap<>();
    private static final String PETSTORE_API_URL = "https://petstore.swagger.io/v2/pet/findByStatus?status={status}";

    @PostMapping("/fetch")
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        String statusFilter = request.getStatus() != null && !request.getStatus().isBlank()
            ? request.getStatus() : "available";
        logger.info("Fetching pets with status={}", statusFilter);
        try {
            JsonNode responseJson = restTemplate.getForObject(PETSTORE_API_URL, JsonNode.class, statusFilter);
            if (responseJson == null || !responseJson.isArray()) {
                logger.error("Invalid response from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from external API");
            }
            petStorage.clear();
            for (JsonNode node : responseJson) {
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
                petStorage.put(pet.getId(), pet);
            }
            List<Pet> list = petStorage.values().stream().collect(Collectors.toList());
            FetchPetsResponse resp = new FetchPetsResponse();
            resp.setPets(list);
            logger.info("Stored {} pets", list.size());
            return ResponseEntity.ok(resp);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error fetching pets", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch data");
        }
    }

    @GetMapping
    public ResponseEntity<GetPetsResponse> getPets() {
        logger.info("Retrieving {} stored pets", petStorage.size());
        GetPetsResponse resp = new GetPetsResponse();
        resp.setPets(petStorage.values().stream().collect(Collectors.toList()));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptPetResponse> adoptPet(@RequestBody @Valid AdoptPetRequest request) {
        logger.info("Adopt request for petId={}", request.getPetId());
        Pet pet = petStorage.get(request.getPetId());
        if (pet == null) {
            logger.error("Pet {} not found", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if ("adopted".equalsIgnoreCase(pet.getStatus())) {
            return ResponseEntity.ok(new AdoptPetResponse("Pet already adopted", pet));
        }
        pet.setStatus("adopted");
        petStorage.put(pet.getId(), pet);
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