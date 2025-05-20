package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<Integer, PetSummary> petStore = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, PetDetails> petDetailsStore = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private static final String EXTERNAL_API_BASE = "https://petstore.swagger.io/v2";

    @PostMapping(value = "/fetch", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FetchPetsResponse> fetchPets(@RequestBody @Valid FetchPetsRequest request) {
        log.info("Received fetch request with type={} status={}", request.getType(), request.getStatus());
        CompletableFuture.runAsync(() -> {
            try {
                String url = EXTERNAL_API_BASE + "/pet/findByStatus?status=" +
                        (request.getStatus() != null ? request.getStatus() : "available,pending,sold");
                String response = restTemplate.getForObject(url, String.class);
                JsonNode rootNode = objectMapper.readTree(response);
                petStore.clear();
                int count = 0;
                if (rootNode.isArray()) {
                    for (JsonNode petNode : rootNode) {
                        PetSummary pet = parsePetSummary(petNode);
                        if (request.getType() == null || request.getType().equalsIgnoreCase(pet.getType())) {
                            petStore.put(pet.getId(), pet);
                            count++;
                        }
                    }
                }
                log.info("Processed {} pets", count);
            } catch (Exception e) {
                log.error("Error fetching pets", e);
            }
        }, executor);
        return ResponseEntity.ok(new FetchPetsResponse("Pets data fetch started", petStore.size()));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PetSummary>> getPets() {
        log.info("Returning {} pets", petStore.size());
        return ResponseEntity.ok(List.copyOf(petStore.values()));
    }

    @PostMapping(value = "/details", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FetchPetDetailsResponse> fetchPetDetails(@RequestBody @Valid FetchPetDetailsRequest request) {
        log.info("Fetching details for petId={}", request.getPetId());
        try {
            String url = EXTERNAL_API_BASE + "/pet/" + request.getPetId();
            String response = restTemplate.getForObject(url, String.class);
            JsonNode petNode = objectMapper.readTree(response);
            PetDetails details = parsePetDetails(petNode);
            petDetailsStore.put(request.getPetId(), details);
            return ResponseEntity.ok(new FetchPetDetailsResponse("Pet details fetched and stored", request.getPetId()));
        } catch (Exception e) {
            log.error("Error fetching pet details", e);
            throw new ResponseStatusException(e instanceof ResponseStatusException ? ((ResponseStatusException) e).getStatusCode() : null,
                    e.getMessage());
        }
    }

    @GetMapping(value = "/{petId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PetDetails> getPetDetails(@PathVariable @Min(1) int petId) {
        PetDetails details = petDetailsStore.get(petId);
        if (details == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found " + petId);
        }
        return ResponseEntity.ok(details);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        log.error("Handled error: {}", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(ex.getReason()));
    }

    private PetSummary parsePetSummary(JsonNode node) {
        PetSummary pet = new PetSummary();
        pet.setId(node.path("id").asInt());
        pet.setName(node.path("name").asText(null));
        pet.setStatus(node.path("status").asText(null));
        pet.setType(node.path("category").path("name").asText(null));
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            pet.setPhotoUrls(objectMapper.convertValue(node.get("photoUrls"), List.class));
        }
        return pet;
    }

    private PetDetails parsePetDetails(JsonNode node) {
        PetDetails details = new PetDetails();
        details.setId(node.path("id").asInt());
        details.setName(node.path("name").asText(null));
        details.setStatus(node.path("status").asText(null));
        details.setType(node.path("category").path("name").asText(null));
        if (node.has("category")) {
            JsonNode c = node.get("category");
            details.setCategory(new Category(c.path("id").asInt(), c.path("name").asText(null)));
        }
        if (node.has("photoUrls")) {
            details.setPhotoUrls(objectMapper.convertValue(node.get("photoUrls"), List.class));
        }
        if (node.has("tags")) {
            details.setTags(objectMapper.convertValue(node.get("tags"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Tag.class)));
        }
        return details;
    }

    @Data
    public static class FetchPetsRequest {
        @Size(max = 30)
        private String type;
        @Size(max = 30)
        private String status;
    }

    @Data
    @AllArgsConstructor
    public static class FetchPetsResponse {
        private String message;
        private int count;
    }

    @Data
    public static class FetchPetDetailsRequest {
        @Min(1)
        private int petId;
    }

    @Data
    @AllArgsConstructor
    public static class FetchPetDetailsResponse {
        private String message;
        private int petId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetSummary {
        private int id;
        private String name;
        private String type;
        private String status;
        private List<String> photoUrls;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PetDetails {
        private int id;
        private String name;
        private String type;
        private String status;
        private Category category;
        private List<String> photoUrls;
        private List<Tag> tags;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Category {
        private int id;
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Tag {
        private int id;
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
    }
}