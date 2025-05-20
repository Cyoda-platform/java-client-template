package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2/pet";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<Long, Pet> petStorage = new ConcurrentHashMap<>();
    private final Map<String, JobStatus> entityJobs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        logger.info("EntityControllerPrototype initialized");
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseWrapper addOrUpdatePet(@Valid @RequestBody PetRequest petRequest) {
        logger.info("Received addOrUpdatePet request: {}", petRequest);
        try {
            JsonNode petData;
            if (petRequest.getPetId() != null) {
                String url = PETSTORE_API_BASE + "/" + petRequest.getPetId();
                String response = restTemplate.getForObject(url, String.class);
                petData = objectMapper.readTree(response);
                logger.info("Fetched pet data from external API for petId={}", petRequest.getPetId());
            } else {
                petData = objectMapper.valueToTree(petRequest);
                logger.info("Creating new pet from request data");
            }
            Pet pet = mapJsonNodeToPet(petData, petRequest);
            petStorage.put(pet.getPetId(), pet);
            logger.info("Pet stored/updated internally with petId={}", pet.getPetId());
            CompletableFuture.runAsync(() -> {
                logger.info("Async processing for petId={}", pet.getPetId());
                // TODO: trigger event-driven workflows
            });
            return new ResponseWrapper(true, pet);
        } catch (IOException e) {
            logger.error("Failed to parse external pet data", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pet data from external API");
        } catch (Exception e) {
            logger.error("Unexpected error in addOrUpdatePet", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        }
    }

    @GetMapping(value = "/{petId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPetById(@PathVariable @Positive Long petId) {
        logger.info("Received getPetById request for petId={}", petId);
        Pet pet = petStorage.get(petId);
        if (pet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Pet> searchPets(
        @RequestParam(required = false) @Pattern(regexp = "available|pending|sold") String status,
        @RequestParam(required = false) @Size(max = 50) String category
    ) {
        logger.info("Received searchPets request with status='{}', category='{}'", status, category);
        List<Pet> results = new ArrayList<>();
        for (Pet pet : petStorage.values()) {
            boolean matches = true;
            if (status != null && !status.equalsIgnoreCase(pet.getStatus())) matches = false;
            if (category != null && !category.equalsIgnoreCase(pet.getCategory())) matches = false;
            if (matches) results.add(pet);
        }
        logger.info("searchPets found {} matching pets", results.size());
        return results;
    }

    @PostMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public DeleteResponse deletePet(@Valid @RequestBody DeleteRequest deleteRequest) {
        logger.info("Received deletePet request for petId={}", deleteRequest.getPetId());
        Pet removed = petStorage.remove(deleteRequest.getPetId());
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        logger.info("Pet deleted with petId={}", deleteRequest.getPetId());
        return new DeleteResponse(true);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Map<String, Object> handleResponseStatusException(ResponseStatusException e) {
        logger.error("Handling ResponseStatusException: {}", e.getReason());
        return Map.of(
            "status", e.getStatusCode().value(),
            "error", e.getReason()
        );
    }

    private Pet mapJsonNodeToPet(JsonNode petData, PetRequest overrides) {
        Long petId = null;
        if (petData.hasNonNull("id") && petData.get("id").canConvertToLong()) {
            petId = petData.get("id").asLong();
        }
        if (petId == null && overrides.getPetId() != null) {
            petId = overrides.getPetId();
        }
        if (petId == null) {
            petId = new Random().nextLong() & Long.MAX_VALUE;
            logger.info("Generated new petId={}", petId);
        }
        String name = firstNonNullString(
            overrides.getName(),
            petData.hasNonNull("name") ? petData.get("name").asText() : null,
            "Unnamed Pet"
        );
        String category = firstNonNullString(
            overrides.getCategory(),
            petData.has("category") && petData.get("category").hasNonNull("name") ? petData.get("category").get("name").asText() : null,
            "Unknown"
        );
        String status = firstNonNullString(
            overrides.getStatus(),
            petData.hasNonNull("status") ? petData.get("status").asText() : null,
            "available"
        );
        List<String> tags = new ArrayList<>();
        if (overrides.getTags() != null && !overrides.getTags().isEmpty()) {
            tags.addAll(overrides.getTags());
        } else if (petData.has("tags") && petData.get("tags").isArray()) {
            for (JsonNode tagNode : petData.get("tags")) {
                if (tagNode.hasNonNull("name")) tags.add(tagNode.get("name").asText());
            }
        }
        return new Pet(petId, name, category, status, tags);
    }

    private String firstNonNullString(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetRequest {
        @Positive
        private Long petId;
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Size(max = 50)
        private String category;
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(max = 10) // max 10 tags
        private List<@NotBlank @Size(max = 30) String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long petId;
        private String name;
        private String category;
        private String status;
        private List<String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseWrapper {
        private boolean success;
        private Pet pet;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteRequest {
        @NotNull
        @Positive
        private Long petId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteResponse {
        private boolean success;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class JobStatus {
        private String status;
        private Instant requestedAt;
    }
}