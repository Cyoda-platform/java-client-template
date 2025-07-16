package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile long idSeq = 1L;

    @Data
    public static class PetRequest {
        @NotBlank
        @Pattern(regexp = "fetch|add|update", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String action;
        @Min(1)
        private Long id; // required for update
        @Size(min = 1, max = 100)
        private String name; // required for add/update
        @Size(min = 1, max = 50)
        private String category; // required for add/update
        @Pattern(regexp = "available|pending|sold", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String status; // required for add/update
    }

    @Data
    public static class SearchRequest {
        @Pattern(regexp = "available|pending|sold", flags = Pattern.Flag.CASE_INSENSITIVE)
        private String status;
        @Size(min = 1, max = 50)
        private String category;
        @Size(min = 1, max = 50)
        private String nameContains;
    }

    @Data
    public static class Pet {
        private Long id;
        private String name;
        private String category;
        private String status;
    }

    @PostMapping
    public ResponseEntity<?> postPets(@Valid @RequestBody PetRequest request) {
        String action = request.getAction().toLowerCase().trim();
        logger.info("POST /prototype/pets action={}", action);
        switch (action) {
            case "fetch":
                try {
                    String externalUrl = "https://petstore.swagger.io/v2/pet/1"; // TODO replace with dynamic ID or endpoint
                    String resp = restTemplate.getForObject(externalUrl, String.class);
                    JsonNode root = objectMapper.readTree(resp);
                    Pet pet = new Pet();
                    pet.setId(idSeq++);
                    pet.setName(root.path("name").asText("Unknown"));
                    pet.setCategory(root.path("category").path("name").asText("Unknown"));
                    pet.setStatus(root.path("status").asText("available"));
                    petStore.put(pet.getId(), pet);
                    logger.info("Fetched pet {}", pet);
                    return ResponseEntity.ok(Map.of("success", true, "pet", pet, "message", "Fetched pet"));
                } catch (Exception e) {
                    logger.error("Error fetching pet", e);
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pet");
                }
            case "add":
                if (request.getName() == null || request.getCategory() == null || request.getStatus() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing fields for add");
                }
                Pet newPet = new Pet();
                newPet.setId(idSeq++);
                newPet.setName(request.getName());
                newPet.setCategory(request.getCategory());
                newPet.setStatus(request.getStatus());
                petStore.put(newPet.getId(), newPet);
                logger.info("Added pet {}", newPet);
                return ResponseEntity.ok(Map.of("success", true, "pet", newPet, "message", "Pet added"));
            case "update":
                if (request.getId() == null || request.getName() == null || request.getCategory() == null || request.getStatus() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing fields for update");
                }
                Pet existing = petStore.get(request.getId());
                if (existing == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                }
                existing.setName(request.getName());
                existing.setCategory(request.getCategory());
                existing.setStatus(request.getStatus());
                logger.info("Updated pet {}", existing);
                return ResponseEntity.ok(Map.of("success", true, "pet", existing, "message", "Pet updated"));
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported action");
        }
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchPets(@Valid @RequestBody SearchRequest request) {
        logger.info("POST /prototype/pets/search filters={}", request);
        List<Pet> results = petStore.values().stream()
            .filter(p -> (request.getStatus() == null || p.getStatus().equalsIgnoreCase(request.getStatus())))
            .filter(p -> (request.getCategory() == null || p.getCategory().toLowerCase().contains(request.getCategory().toLowerCase())))
            .filter(p -> (request.getNameContains() == null || p.getName().toLowerCase().contains(request.getNameContains().toLowerCase())))
            .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("results", results));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPetById(@PathVariable @Min(1) Long id) {
        logger.info("GET /prototype/pets/{}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @GetMapping
    public ResponseEntity<?> listAllPets() {
        logger.info("GET /prototype/pets");
        return ResponseEntity.ok(petStore.values().stream().toList());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleError(ResponseStatusException ex) {
        logger.error("Error {}: {}", ex.getStatusCode(), ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getStatusCode().toString(), "message", ex.getReason()));
    }
}