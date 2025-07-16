package com.java_template.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping(path = "/prototype/pets", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);
    private final WebClient webClient = WebClient.create("https://petstore.swagger.io/v2");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, JsonNode> lastSearchResults = new ConcurrentHashMap<>();
    private final Map<Long, PetAdoptionRecord> adoptedPets = new ConcurrentHashMap<>();

    @PostMapping("/search")
    public List<JsonNode> searchPets(@Valid @RequestBody SearchRequest request) {
        logger.info("Received search request: type='{}', status='{}'", request.getType(), request.getStatus());
        try {
            String statusParam = (request.getStatus() != null && !request.getStatus().isBlank()) ? request.getStatus() : "available";
            JsonNode petsNode = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/pet/findByStatus").queryParam("status", statusParam).build())
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    try {
                        return objectMapper.readTree(body);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse external API response", e);
                    }
                })
                .block();
            if (petsNode == null || !petsNode.isArray()) {
                logger.error("Invalid response from Petstore API");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid response from Petstore API");
            }
            List<JsonNode> results = new ArrayList<>();
            for (JsonNode pet : petsNode) {
                if (request.getType() != null && !request.getType().isBlank()) {
                    if (pet.has("category") && pet.get("category").has("name") &&
                        pet.get("category").get("name").asText().equalsIgnoreCase(request.getType())) {
                        results.add(pet);
                    }
                } else {
                    results.add(pet);
                }
            }
            lastSearchResults.clear();
            for (JsonNode pet : results) {
                lastSearchResults.put(pet.get("id").asLong(), pet);
            }
            return results;
        } catch (Exception e) {
            logger.error("Error during searching pets", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error fetching pets");
        }
    }

    @GetMapping
    public List<JsonNode> getCachedPets() {
        logger.info("Returning cached last search results");
        return List.copyOf(lastSearchResults.values());
    }

    @PostMapping("/adopt")
    public AdoptionResponse adoptPet(@Valid @RequestBody AdoptionRequest request) {
        logger.info("Adopt pet request: petId={}, adopterName={}", request.getPetId(), request.getAdopterName());
        if (adoptedPets.containsKey(request.getPetId())) {
            logger.error("Pet {} already adopted", request.getPetId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pet already adopted");
        }
        CompletableFuture.runAsync(() -> {
            logger.info("Simulating external adoption update for petId {}", request.getPetId());
            // TODO: replace with actual external API call for status update
        });
        PetAdoptionRecord record = new PetAdoptionRecord(request.getPetId(), request.getAdopterName(), Instant.now());
        adoptedPets.put(request.getPetId(), record);
        return new AdoptionResponse(true, "Pet adopted successfully", request.getPetId());
    }

    @GetMapping("/{id}")
    public JsonNode getPetById(@PathVariable("id") Long petId) {
        logger.info("Fetch details for id={}", petId);
        JsonNode petNode = lastSearchResults.get(petId);
        if (petNode == null) {
            logger.error("Pet id={} not found", petId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
        }
        if (adoptedPets.containsKey(petId)) {
            PetAdoptionRecord record = adoptedPets.get(petId);
            try {
                JsonNode copy = objectMapper.readTree(petNode.toString());
                ((com.fasterxml.jackson.databind.node.ObjectNode) copy).put("status", "adopted");
                ((com.fasterxml.jackson.databind.node.ObjectNode) copy).put("adopterName", record.getAdopterName());
                return copy;
            } catch (Exception e) {
                logger.error("Failed to augment pet details", e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error composing pet details");
            }
        }
        return petNode;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("HTTP error: {}", ex.getStatusCode());
        ErrorResponse error = new ErrorResponse(ex.getStatusCode().toString(), ex.getReason());
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    @Data
    public static class SearchRequest {
        @Size(max = 20)
        private String type;
        @Size(max = 20)
        private String status;
    }

    @Data
    public static class AdoptionRequest {
        @NotNull
        private Long petId;
        @NotBlank
        private String adopterName;
    }

    @Data
    public static class AdoptionResponse {
        private final boolean success;
        private final String message;
        private final Long petId;
    }

    @Data
    public static class PetAdoptionRecord {
        private final Long petId;
        private final String adopterName;
        private final Instant adoptedAt;
    }

    @Data
    public static class ErrorResponse {
        private final String error;
        private final String message;
    }
}