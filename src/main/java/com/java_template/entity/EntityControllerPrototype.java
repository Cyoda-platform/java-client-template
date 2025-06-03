package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, Pet> pets = new ConcurrentHashMap<>();
    private final Map<String, AdoptionRequest> adoptionRequests = new ConcurrentHashMap<>();

    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public FetchResponse fetchPets(@RequestBody @Valid FetchFilter filter) {
        log.info("Received fetchPets request with filter: {}", filter);
        String url = PETSTORE_API_BASE + "/pet/findByStatus?status=" + filter.getStatus();
        try {
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Empty response from external Petstore API");
            }
            JsonNode rootNode = objectMapper.readTree(response);
            pets.clear();
            int count = 0;
            if (rootNode.isArray()) {
                for (JsonNode petNode : rootNode) {
                    Pet pet = parsePetFromJson(petNode);
                    if (pet != null) {
                        pets.put(pet.getId(), pet);
                        count++;
                    }
                }
            } else {
                log.warn("Unexpected JSON response structure from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected response structure");
            }
            log.info("Fetched and updated {} pets from external API", count);
            return new FetchResponse("Fetched and updated pets successfully", count);
        } catch (Exception e) {
            log.error("Error fetching pets from external API", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to fetch pets from external API");
        }
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Collection<Pet> listPets() {
        log.info("Listing all pets, count={}", pets.size());
        return pets.values();
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Pet getPet(@PathVariable("id") @NotNull Long id) {
        log.info("Fetching pet with id={}", id);
        Pet pet = pets.get(id);
        if (pet == null) {
            log.warn("Pet with id={} not found", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        return pet;
    }

    @PostMapping(value = "/adopt", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AdoptionResponse adoptPet(@RequestBody @Valid AdoptionRequest request) {
        log.info("Received adoption request: {}", request);
        Pet pet = pets.get(request.getPetId());
        if (pet == null) {
            log.warn("Adoption attempt for non-existing pet id={}", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            log.warn("Pet id={} is not available for adoption, current status={}", pet.getId(), pet.getStatus());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pet is not available for adoption");
        }
        pet.setStatus("pending");
        pets.put(pet.getId(), pet);
        String adoptionId = UUID.randomUUID().toString();
        request.setRequestId(adoptionId);
        request.setRequestTime(Instant.now());
        adoptionRequests.put(adoptionId, request);
        CompletableFuture.runAsync(() -> {
            log.info("Processing adoption request asynchronously: {}", adoptionId);
            // TODO: notify external systems or update persistence
        });
        return new AdoptionResponse("Adoption request received", pet.getId(), pet.getStatus());
    }

    private Pet parsePetFromJson(JsonNode petNode) {
        try {
            Long id = petNode.path("id").asLong();
            String name = petNode.path("name").asText(null);
            String status = petNode.path("status").asText(null);
            JsonNode categoryNode = petNode.path("category");
            String category = !categoryNode.isMissingNode() && !categoryNode.isNull()
                    ? categoryNode.path("name").asText(null) : null;
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = petNode.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode tagNode : tagsNode) {
                    String tagName = tagNode.path("name").asText(null);
                    if (tagName != null) tags.add(tagName);
                }
            }
            List<String> photoUrls = new ArrayList<>();
            JsonNode photosNode = petNode.path("photoUrls");
            if (photosNode.isArray()) {
                for (JsonNode urlNode : photosNode) {
                    if (urlNode.isTextual()) photoUrls.add(urlNode.asText());
                }
            }
            return new Pet(id, name, status, category, tags, photoUrls);
        } catch (Exception e) {
            log.error("Failed to parse pet from external API JSON node: {}", petNode, e);
            return null;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private Long id;
        private String name;
        private String status;
        private String category;
        private List<String> tags;
        private List<String> photoUrls;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchFilter {
        @NotBlank
        @Pattern(regexp = "available|pending|sold")
        private String status;
        @Size(max = 10)
        private List<@NotBlank String> tags;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FetchResponse {
        private String message;
        private int count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionRequest {
        private String requestId;
        @NotNull
        private Long petId;
        @NotBlank
        private String adopterName;
        @NotBlank
        private String contactInfo;
        private Instant requestTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdoptionResponse {
        private String message;
        private Long petId;
        private String status;
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseStatus
    public Map<String, Object> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled ResponseStatusException: {}", ex.getReason());
        Map<String, Object> error = new HashMap<>();
        error.put("status", ex.getStatusCode().value());
        error.put("error", ex.getStatusCode().getReasonPhrase());
        error.put("message", ex.getReason());
        return error;
    }
}