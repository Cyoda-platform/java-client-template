package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping(path = "/prototype/pets")
@RequiredArgsConstructor
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    private final Map<Long, Pet> petStore = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private long idSequence = 1;
    private static final String EXTERNAL_PETSTORE_API = "https://petstore3.swagger.io/api/v3/pet";

    @Data
    public static class Pet {
        private Long id;
        @NotBlank
        private String name;
        @NotBlank
        private String category;
        @Size(max = 5)
        private List<@NotBlank String> tags = new ArrayList<>();
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    public static class PetSearchRequest {
        private String category;
        private String status;
        @Size(max = 5)
        private List<@NotBlank String> tags = new ArrayList<>();
    }

    @Data
    public static class PetSearchResponse {
        private List<Pet> results = new ArrayList<>();
    }

    @Data
    public static class FunFactResponse {
        @NotBlank
        private String fact;
    }

    @Data
    public static class AddOrUpdatePetResponse {
        private boolean success;
        private Pet pet;
    }

    @PostMapping
    public ResponseEntity<AddOrUpdatePetResponse> addOrUpdatePet(@RequestBody @Valid Pet petRequest) {
        logger.info("POST /prototype/pets request: {}", petRequest);
        try {
            Pet petToSave;
            if (petRequest.getId() == null) {
                petToSave = new Pet();
                petToSave.setId(generateId());
            } else {
                petToSave = petStore.getOrDefault(petRequest.getId(), new Pet());
                petToSave.setId(petRequest.getId());
            }
            petToSave.setName(petRequest.getName());
            petToSave.setCategory(petRequest.getCategory());
            petToSave.setTags(petRequest.getTags());
            petToSave.setStatus(petRequest.getStatus());

            if (petRequest.getId() != null) {
                try {
                    String url = EXTERNAL_PETSTORE_API + "/" + petRequest.getId();
                    String ext = restTemplate.getForObject(url, String.class);
                    JsonNode node = objectMapper.readTree(ext);
                    logger.info("Validated external pet id {}: {}", petRequest.getId(), node);
                } catch (Exception ex) {
                    logger.warn("External validation failed for id {}: {}", petRequest.getId(), ex.getMessage());
                }
            }

            petStore.put(petToSave.getId(), petToSave);
            AddOrUpdatePetResponse resp = new AddOrUpdatePetResponse();
            resp.setSuccess(true);
            resp.setPet(petToSave);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Error in addOrUpdatePet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                              "Internal error while adding/updating pet", e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) {
        logger.info("GET /prototype/pets/{} request", id);
        try {
            Long petId = Long.valueOf(id);
            Pet pet = petStore.get(petId);
            if (pet == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
            }
            return ResponseEntity.ok(pet);
        } catch (NumberFormatException e) {
            logger.error("Invalid id format: {}", id);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pet ID format");
        }
    }

    @PostMapping("/search")
    public ResponseEntity<PetSearchResponse> searchPets(@RequestBody @Valid PetSearchRequest searchRequest) {
        logger.info("POST /prototype/pets/search request: {}", searchRequest);
        try {
            List<Pet> filtered = new ArrayList<>();
            for (Pet p : petStore.values()) {
                if (matchesSearch(p, searchRequest)) {
                    filtered.add(p);
                }
            }
            try {
                String url = EXTERNAL_PETSTORE_API + "/findByStatus?status=available";
                String ext = restTemplate.getForObject(url, String.class);
                JsonNode arr = objectMapper.readTree(ext);
                if (arr.isArray()) {
                    for (JsonNode node : arr) {
                        Pet extPet = jsonNodeToPet(node);
                        if (matchesSearch(extPet, searchRequest)) {
                            filtered.add(extPet);
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("External search enrich failed: {}", ex.getMessage());
            }
            PetSearchResponse resp = new PetSearchResponse();
            resp.setResults(filtered);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Error in searchPets", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                              "Internal error while searching pets", e);
        }
    }

    @PostMapping("/fun/fact")
    public ResponseEntity<FunFactResponse> randomPetFact() {
        logger.info("POST /prototype/pets/fun/fact request");
        try {
            List<String> facts = List.of(
                "Cats sleep for 70% of their lives.",
                "Dogs have three eyelids.",
                "Goldfish can distinguish music genres.",
                "Rabbits can't vomit."
            );
            String fact = facts.get(new Random().nextInt(facts.size()));
            FunFactResponse resp = new FunFactResponse();
            resp.setFact(fact);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Error in randomPetFact", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                              "Internal error while retrieving pet fact", e);
        }
    }

    private synchronized long generateId() {
        return idSequence++;
    }

    private boolean matchesSearch(Pet pet, PetSearchRequest req) {
        if (req.getCategory() != null && !pet.getCategory().equalsIgnoreCase(req.getCategory())) {
            return false;
        }
        if (req.getStatus() != null && !pet.getStatus().equalsIgnoreCase(req.getStatus())) {
            return false;
        }
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            for (String tag : req.getTags()) {
                if (pet.getTags().stream().noneMatch(t -> t.equalsIgnoreCase(tag))) {
                    return false;
                }
            }
        }
        return true;
    }

    private Pet jsonNodeToPet(JsonNode node) {
        Pet pet = new Pet();
        pet.setId(node.has("id") && !node.get("id").isNull() ? node.get("id").asLong() : null);
        pet.setName(node.path("name").asText(null));
        if (node.has("category") && node.get("category").has("name")) {
            pet.setCategory(node.get("category").get("name").asText());
        }
        List<String> tags = new ArrayList<>();
        if (node.has("tags") && node.get("tags").isArray()) {
            for (JsonNode t : node.get("tags")) {
                if (t.has("name")) {
                    tags.add(t.get("name").asText());
                }
            }
        }
        pet.setTags(tags);
        pet.setStatus(node.path("status").asText(null));
        return pet;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        logger.error("Handled error {}: {}", ex.getStatusCode(), ex.getReason());
        return new ResponseEntity<>(err, ex.getStatusCode());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        Map<String, String> err = new HashMap<>();
        err.put("error", HttpStatus.INTERNAL_SERVER_ERROR.toString());
        err.put("message", "Unexpected error occurred");
        logger.error("Unhandled exception: ", ex);
        return new ResponseEntity<>(err, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}