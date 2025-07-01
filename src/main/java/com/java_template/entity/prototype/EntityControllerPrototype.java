package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Validated
@RestController
@RequestMapping("prototype/pets")
public class EntityControllerPrototype {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, Pet> petCache = new ConcurrentHashMap<>();
    private final Map<String, AdoptionRequest> adoptionRequests = new ConcurrentHashMap<>();
    private static final String PETSTORE_BASE = "https://petstore.swagger.io/v2";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class Pet {
        private Long id;
        private String name;
        private String type;
        private String status;
        private String[] photoUrls;
        private String[] tags;
    }

    @Data
    static class SearchRequest {
        @Size(max = 50)
        private String type;
        @Size(max = 20)
        private String status;
        @Size(max = 100)
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SearchResponse {
        private Pet[] pets;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AdoptionRequest {
        private String adoptionId;
        private Long petId;
        private String adopterName;
        private String adopterContact;
        private String status;
        private String message;
        private Instant requestedAt;
    }

    @Data
    static class AdoptRequestBody {
        @NotNull
        @Positive
        private Long petId;
        @NotBlank
        private String adopterName;
        @NotBlank
        private String adopterContact;
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchPets(@RequestBody @Valid SearchRequest request) {
        logger.info("Received search request: type='{}', status='{}', name='{}'",
                request.getType(), request.getStatus(), request.getName());
        try {
            String statusParam = request.getStatus() != null ? request.getStatus() : "available";
            String url = PETSTORE_BASE + "/pet/findByStatus?status=" + statusParam;
            String responseStr = restTemplate.getForObject(url, String.class);
            JsonNode rootNode = objectMapper.readTree(responseStr);
            var petsListNode = rootNode.isArray() ? rootNode : objectMapper.createArrayNode();
            var filteredPetsList = objectMapper.createArrayNode();
            for (JsonNode petNode : petsListNode) {
                boolean matches = true;
                if (request.getType() != null && !request.getType().isBlank()) {
                    String petType = petNode.path("category").path("name").asText("");
                    if (!petType.equalsIgnoreCase(request.getType())) {
                        matches = false;
                    }
                }
                if (request.getName() != null && !request.getName().isBlank()) {
                    String petName = petNode.path("name").asText("");
                    if (!petName.toLowerCase().contains(request.getName().toLowerCase())) {
                        matches = false;
                    }
                }
                if (matches) {
                    filteredPetsList.add(petNode);
                }
            }
            Pet[] petsArray = new Pet[filteredPetsList.size()];
            int idx = 0;
            for (JsonNode petNode : filteredPetsList) {
                Pet pet = mapJsonNodeToPet(petNode);
                if (pet != null) {
                    petCache.put(pet.getId(), pet);
                    petsArray[idx++] = pet;
                }
            }
            logger.info("Search returned {} pets", idx);
            return ResponseEntity.ok(new SearchResponse(petsArray));
        } catch (Exception e) {
            logger.error("Error during pet search", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error searching pets: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable @NotNull @Positive Long id) {
        logger.info("Get pet by id: {}", id);
        Pet pet = petCache.get(id);
        if (pet == null) {
            logger.error("Pet with id {} not found in cache", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Pet not found with id " + id);
        }
        return ResponseEntity.ok(pet);
    }

    @PostMapping("/adopt")
    public ResponseEntity<AdoptionRequest> adoptPet(@RequestBody @Valid AdoptRequestBody request) {
        logger.info("Adoption request received for petId={} by {}", request.getPetId(), request.getAdopterName());
        Pet pet = petCache.get(request.getPetId());
        if (pet == null) {
            logger.error("Pet with id {} not found in cache for adoption", request.getPetId());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Pet not found with id " + request.getPetId());
        }
        if (!"available".equalsIgnoreCase(pet.getStatus())) {
            logger.info("Pet id={} is not available for adoption. Status={}", pet.getId(), pet.getStatus());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new AdoptionRequest(
                            null,
                            pet.getId(),
                            request.getAdopterName(),
                            request.getAdopterContact(),
                            "rejected",
                            "Pet is not available for adoption",
                            Instant.now()));
        }
        String adoptionId = UUID.randomUUID().toString();
        AdoptionRequest adoptionRequest = new AdoptionRequest(
                adoptionId,
                pet.getId(),
                request.getAdopterName(),
                request.getAdopterContact(),
                "pending",
                "Your adoption request is pending approval",
                Instant.now());
        adoptionRequests.put(adoptionId, adoptionRequest);
        CompletableFuture.runAsync(() -> processAdoption(adoptionId)); // TODO: asynchronous processing
        logger.info("Adoption request {} stored and processing started", adoptionId);
        return ResponseEntity.ok(adoptionRequest);
    }

    @GetMapping("/adoptions/{adoptionId}")
    public ResponseEntity<AdoptionRequest> getAdoptionStatus(@PathVariable @NotBlank String adoptionId) {
        logger.info("Retrieve adoption status for id: {}", adoptionId);
        AdoptionRequest adoption = adoptionRequests.get(adoptionId);
        if (adoption == null) {
            logger.error("Adoption request with id {} not found", adoptionId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Adoption request not found with id " + adoptionId);
        }
        return ResponseEntity.ok(adoption);
    }

    private Pet mapJsonNodeToPet(JsonNode petNode) {
        try {
            Long id = petNode.path("id").asLong();
            String name = petNode.path("name").asText("");
            String type = petNode.path("category").path("name").asText("");
            String status = petNode.path("status").asText("");
            String[] photoUrls = objectMapper.convertValue(petNode.path("photoUrls"), String[].class);
            String[] tags = objectMapper.convertValue(petNode.path("tags").findValuesAsText("name"), String[].class);
            return new Pet(id, name, type, status, photoUrls, tags);
        } catch (Exception e) {
            logger.error("Error mapping pet JSON to Pet DTO", e);
            return null;
        }
    }

    private void processAdoption(String adoptionId) {
        logger.info("Processing adoption asynchronously for id: {}", adoptionId);
        try {
            Thread.sleep(3000);
            AdoptionRequest adoption = adoptionRequests.get(adoptionId);
            if (adoption == null) {
                logger.error("Adoption request not found during processing: {}", adoptionId);
                return;
            }
            adoption.setStatus("approved");
            adoption.setMessage("Your adoption request has been approved! Thank you.");
            Pet pet = petCache.get(adoption.getPetId());
            if (pet != null) {
                pet.setStatus("sold");
                petCache.put(pet.getId(), pet);
            }
            logger.info("Adoption request {} approved and pet status updated", adoptionId);
        } catch (InterruptedException e) {
            logger.error("Adoption processing interrupted for id: {}", adoptionId, e);
            Thread.currentThread().interrupt();
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Handling ResponseStatusException: {}", ex.getReason(), ex);
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", ex.getStatusCode().toString(),
                        "message", ex.getReason()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Unhandled exception occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", HttpStatus.INTERNAL_SERVER_ERROR.toString(),
                        "message", "Internal server error"
                ));
    }
}