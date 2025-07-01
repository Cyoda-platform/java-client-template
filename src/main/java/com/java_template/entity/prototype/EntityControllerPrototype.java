package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Pet> petsCache = new ConcurrentHashMap<>();
    private final Map<String, AdoptionRecord> adoptions = new ConcurrentHashMap<>();

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized.");
    }

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ProcessedPetsResponse fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        logger.info("Received fetchPets request type={} status={}", request.getType(), request.getStatus());
        try {
            String statusParam = request.getStatus();
            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusParam;
            logger.info("Calling external Petstore API: {}", url);
            String jsonResponse = restTemplate.getForObject(url, String.class);
            if (jsonResponse == null) {
                logger.error("Petstore API returned null response");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned null");
            }
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            List<Pet> processedPets = new ArrayList<>();
            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    String type = petNode.hasNonNull("category") && petNode.get("category").hasNonNull("name")
                            ? petNode.get("category").get("name").asText().toLowerCase()
                            : "dog";
                    if (!"all".equalsIgnoreCase(request.getType()) &&
                        !request.getType().equalsIgnoreCase(type)) {
                        continue;
                    }
                    Pet pet = new Pet();
                    pet.setId(petNode.hasNonNull("id") ? petNode.get("id").asText() : UUID.randomUUID().toString());
                    pet.setName(petNode.hasNonNull("name") ? petNode.get("name").asText() : "Unnamed");
                    pet.setType(type);
                    pet.setStatus(petNode.hasNonNull("status") ? petNode.get("status").asText() : "available");
                    pet.setAge((int) (Math.random() * 15) + 1); // TODO: replace with real age if available
                    pet.setFunFact(generateFunFactForPet(type));
                    processedPets.add(pet);
                    petsCache.put(pet.getId(), pet);
                }
            } else {
                logger.error("Unexpected JSON structure from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected Petstore API response");
            }
            logger.info("Processed {} pets", processedPets.size());
            return new ProcessedPetsResponse(processedPets);
        } catch (Exception e) {
            logger.error("Error fetching pets: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error contacting Petstore API");
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public PetsResponse getPets() {
        logger.info("Returning {} cached pets", petsCache.size());
        return new PetsResponse(new ArrayList<>(petsCache.values()));
    }

    @PostMapping(value = "/adopt", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AdoptionResponse adoptPet(@RequestBody @Valid AdoptionRequest request) {
        logger.info("Adoption request for petId={} by {}", request.getPetId(), request.getAdopterName());
        Pet pet = petsCache.get(request.getPetId());
        if (pet == null) {
            logger.error("Pet not found with id {}", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if ("sold".equalsIgnoreCase(pet.getStatus())) {
            logger.error("Pet {} already adopted", request.getPetId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pet already adopted");
        }
        pet.setStatus("sold");
        petsCache.put(pet.getId(), pet);
        AdoptionRecord record = new AdoptionRecord(request.getPetId(), request.getAdopterName(), request.getAdoptionDate());
        adoptions.put(request.getPetId(), record);
        logger.info("Adoption recorded for petId {}", request.getPetId());
        // TODO: async notification logic
        CompletableFuture.runAsync(() -> {
            logger.info("Async processing for adoption {}", request.getPetId());
        });
        return new AdoptionResponse(true, "Pet adoption recorded successfully");
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable("id") @NotBlank String id) {
        logger.info("Fetching pet {}", id);
        Pet pet = petsCache.get(id);
        if (pet == null) {
            logger.error("Pet not found {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: status={}, message={}", ex.getStatusCode(), ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getStatusCode().toString());
        error.put("message", ex.getReason());
        return error;
    }

    private String generateFunFactForPet(String type) {
        switch (type.toLowerCase()) {
            case "cat": return "Cats sleep 70% of their lives.";
            case "dog": return "Dogs have three eyelids.";
            default:   return "This pet is unique and adorable!";
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchPetsRequest {
        @NotNull
        @Pattern(regexp = "cat|dog|all", message = "type must be cat, dog, or all")
        private String type;
        @NotNull
        @Pattern(regexp = "available|pending|sold", message = "status must be available, pending, or sold")
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessedPetsResponse {
        private List<Pet> processedPets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetsResponse {
        private List<Pet> pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionRequest {
        @NotBlank
        private String petId;
        @NotBlank
        private String adopterName;
        @NotNull
        private LocalDate adoptionDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionResponse {
        private boolean success;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private String id;
        private String name;
        private String type;
        private String status;
        private int age;
        private String funFact;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionRecord {
        private String petId;
        private String adopterName;
        private LocalDate adoptionDate;
    }

}