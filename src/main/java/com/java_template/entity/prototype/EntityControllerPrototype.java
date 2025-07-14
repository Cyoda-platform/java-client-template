package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets")
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Pet> localPets = new ConcurrentHashMap<>();

    @Data
    public static class Pet {
        private String id;
        @NotBlank @Size(max = 100)
        private String name;
        @NotBlank @Size(max = 50)
        private String type;
        @NotNull @Min(0)
        private Integer age;
        @NotBlank @Pattern(regexp = "available|adopted")
        private String status;
    }

    @Data
    public static class SearchCriteria {
        @Size(max = 50)
        private String type;
        @Size(max = 50)
        private String status;
        @Size(max = 100)
        private String name;
    }

    @Data
    public static class UpdatePetRequest {
        @Size(max = 100)
        private String name;
        @Size(max = 50)
        private String type;
        @Min(0)
        private Integer age;
        @Pattern(regexp = "available|adopted")
        private String status;
    }

    @Data
    public static class MessageResponse {
        private String id;
        private String message;
    }

    @GetMapping
    public Collection<Pet> listPets() {
        logger.info("GET /prototype/pets - listPets count={}", localPets.size());
        return localPets.values();
    }

    @PostMapping("/search")
    public List<Pet> searchPets(@RequestBody @Valid SearchCriteria criteria) {
        logger.info("POST /prototype/pets/search - criteria={}", criteria);
        List<Pet> results = new ArrayList<>();
        localPets.values().stream()
                .filter(p -> matchesCriteria(p, criteria))
                .forEach(results::add);
        try {
            String statusParam = criteria.getStatus() != null && !criteria.getStatus().isBlank()
                    ? criteria.getStatus() : "available";
            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + statusParam;
            logger.info("Fetching external pets: {}", url);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    Pet extPet = new Pet();
                    extPet.setId(node.path("id").asText());
                    extPet.setName(node.path("name").asText());
                    JsonNode cat = node.path("category");
                    extPet.setType(cat.isMissingNode() ? "unknown" : cat.path("name").asText("unknown"));
                    extPet.setAge(null);
                    extPet.setStatus(statusParam);
                    if (matchesCriteria(extPet, criteria)) {
                        results.add(extPet);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching external data", e);
        }
        return results;
    }

    @PostMapping
    public MessageResponse addPet(@RequestBody @Valid Pet pet) {
        String id = UUID.randomUUID().toString();
        pet.setId(id);
        localPets.put(id, pet);
        logger.info("POST /prototype/pets - added pet id={}", id);
        MessageResponse resp = new MessageResponse();
        resp.setId(id);
        resp.setMessage("Pet added successfully");
        return resp;
    }

    @PutMapping("/{id}")
    public MessageResponse updatePet(@PathVariable String id, @RequestBody @Valid UpdatePetRequest update) {
        Pet existing = localPets.get(id);
        if (existing == null) {
            logger.error("PUT /prototype/pets/{}/ - pet not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (update.getName() != null) existing.setName(update.getName());
        if (update.getType() != null) existing.setType(update.getType());
        if (update.getAge() != null) existing.setAge(update.getAge());
        if (update.getStatus() != null) existing.setStatus(update.getStatus());
        logger.info("PUT /prototype/pets/{}/ - updated", id);
        MessageResponse resp = new MessageResponse();
        resp.setId(id);
        resp.setMessage("Pet updated successfully");
        return resp;
    }

    @PostMapping("/{id}/adopt")
    public MessageResponse adoptPet(@PathVariable String id) {
        Pet existing = localPets.get(id);
        if (existing == null) {
            logger.error("POST /prototype/pets/{}/adopt - pet not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        existing.setStatus("adopted");
        logger.info("POST /prototype/pets/{}/adopt - adopted", id);
        MessageResponse resp = new MessageResponse();
        resp.setId(id);
        resp.setMessage("Pet adopted successfully");
        return resp;
    }

    private boolean matchesCriteria(Pet pet, SearchCriteria c) {
        if (c.getType() != null && !c.getType().isBlank() && !c.getType().equalsIgnoreCase(pet.getType()))
            return false;
        if (c.getStatus() != null && !c.getStatus().isBlank() && !c.getStatus().equalsIgnoreCase(pet.getStatus()))
            return false;
        if (c.getName() != null && !c.getName().isBlank() && !pet.getName().toLowerCase().contains(c.getName().toLowerCase()))
            return false;
        return true;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage()));
        logger.error("Validation error: {}", errors);
        return errors;
    }
}