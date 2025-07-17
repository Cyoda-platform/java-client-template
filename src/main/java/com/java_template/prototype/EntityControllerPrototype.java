package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@Validated
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore3.swagger.io/api/v3";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Pet> petStore = new ConcurrentHashMap<>();

    @PostMapping(path = "", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AddUpdatePetResponse> addOrUpdatePet(@RequestBody @Valid Pet pet) {
        logger.info("Received add/update for pet id={}", pet.getId());
        if (pet.getId() == null || pet.getId().isBlank()) {
            pet.setId(UUID.randomUUID().toString());
            logger.info("Generated new pet id={}", pet.getId());
        }
        CompletableFuture.runAsync(() -> enrichPetFromExternalApi(pet)); // TODO: adjust enrichment logic
        petStore.put(pet.getId(), pet);
        return ResponseEntity.ok(new AddUpdatePetResponse(true, pet));
    }

    private void enrichPetFromExternalApi(Pet pet) {
        try {
            logger.info("Enriching pet {} from external API", pet.getName());
            String url = PETSTORE_API_BASE + "/pet/findByStatus?status=available";
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    if (node.hasNonNull("name") && pet.getName().equalsIgnoreCase(node.get("name").asText())) {
                        if (node.has("category") && node.get("category").has("name")) {
                            pet.setCategory(node.get("category").get("name").asText());
                        }
                        if (node.has("photoUrls")) {
                            List<String> photos = new ArrayList<>();
                            node.get("photoUrls").forEach(n -> photos.add(n.asText()));
                            pet.setPhotoUrls(photos);
                        }
                        if (node.has("tags")) {
                            List<String> tags = new ArrayList<>();
                            node.get("tags").forEach(n -> { if (n.has("name")) tags.add(n.get("name").asText()); });
                            pet.setTags(tags);
                        }
                        if (node.has("status")) {
                            pet.setStatus(node.get("status").asText());
                        }
                        logger.info("Enriched pet {}", pet.getId());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Enrichment failed for pet {}: {}", pet.getId(), e.getMessage());
        }
    }

    @GetMapping(path = "/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotBlank String id) {
        logger.info("Retrieving pet id={}", id);
        Pet pet = petStore.get(id);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping(path = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Pet>> searchPets(@RequestBody @Valid PetSearchRequest req) {
        logger.info("Searching pets name={}, status={}, category={}", req.getName(), req.getStatus(), req.getCategory());
        List<Pet> results = new ArrayList<>();
        try {
            if (req.getStatus() != null && !req.getStatus().isBlank()) {
                String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + req.getStatus();
                String json = restTemplate.getForObject(url, String.class);
                JsonNode root = objectMapper.readTree(json);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        Pet p = parsePet(node);
                        if (p != null && matches(p, req)) results.add(p);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("External search failed: {}", e.getMessage());
        }
        petStore.values().stream().filter(p -> matches(p, req)).forEach(results::add);
        return ResponseEntity.ok(results);
    }

    private boolean matches(Pet pet, PetSearchRequest req) {
        if (req.getName() != null && !req.getName().isBlank()) {
            if (!pet.getName().equalsIgnoreCase(req.getName())) return false;
        }
        if (req.getCategory() != null && !req.getCategory().isBlank()) {
            if (!pet.getCategory().equalsIgnoreCase(req.getCategory())) return false;
        }
        if (req.getStatus() != null && !req.getStatus().isBlank()) {
            if (!pet.getStatus().equalsIgnoreCase(req.getStatus())) return false;
        }
        return true;
    }

    private Pet parsePet(JsonNode node) {
        try {
            Pet p = new Pet();
            if (node.hasNonNull("id")) p.setId(node.get("id").asText());
            if (node.hasNonNull("name")) p.setName(node.get("name").asText());
            if (node.has("category") && node.get("category").hasNonNull("name"))
                p.setCategory(node.get("category").get("name").asText());
            if (node.hasNonNull("status")) p.setStatus(node.get("status").asText());
            if (node.has("photoUrls")) {
                List<String> photos = new ArrayList<>();
                node.get("photoUrls").forEach(n -> photos.add(n.asText()));
                p.setPhotoUrls(photos);
            }
            if (node.has("tags")) {
                List<String> tags = new ArrayList<>();
                node.get("tags").forEach(n -> { if (n.has("name")) tags.add(n.get("name").asText()); });
                p.setTags(tags);
            }
            return p;
        } catch (Exception e) {
            logger.error("Parse failed: {}", e.getMessage());
            return null;
        }
    }

    @PostMapping(path = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DeletePetResponse> deletePet(@RequestBody @Valid DeletePetRequest req) {
        logger.info("Deleting pet id={}", req.getId());
        Pet removed = petStore.remove(req.getId());
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return ResponseEntity.ok(new DeletePetResponse(true, "Pet deleted successfully"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error {}: {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> err = new HashMap<>();
        err.put("error", ex.getStatusCode().toString());
        err.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(err);
    }

    @Data
    public static class Pet {
        private String id;
        @NotBlank
        private String name;
        @Size(min = 1, max = 50)
        private String category;
        @Size(min = 1, max = 20)
        private String status;
        @Size(max = 10)
        private List<@NotBlank String> tags = new ArrayList<>();
        @Size(max = 10)
        private List<@NotBlank String> photoUrls = new ArrayList<>();
    }

    @Data
    public static class AddUpdatePetResponse {
        private final boolean success;
        private final Pet pet;
    }

    @Data
    public static class PetSearchRequest {
        @Size(max = 50)
        private String name;
        @Size(max = 20)
        private String status;
        @Size(max = 50)
        private String category;
    }

    @Data
    public static class DeletePetRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class DeletePetResponse {
        private final boolean success;
        private final String message;
    }
}