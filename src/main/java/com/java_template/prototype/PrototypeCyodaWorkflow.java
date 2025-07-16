```java
package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENTITY_NAME_PET = "Pet";
    private static final String ENTITY_NAME_ADOPTION_REQUEST = "AdoptionRequest";
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    /**
     * Workflow function to process Pet entity before persistence.
     * This function can modify entity state asynchronously before it is saved.
     * It must return the entity (possibly modified).
     */
    private CompletableFuture<Pet> processPet(Pet pet) {
        // Example: here we could do some async processing or validation before saving
        // For now, just return the same pet wrapped in a completed future
        return CompletableFuture.completedFuture(pet);
    }

    /**
     * Workflow function to process AdoptionRequest entity before persistence.
     */
    private CompletableFuture<AdoptionRequest> processAdoptionRequestEntity(AdoptionRequest adoptionRequest) {
        // Example: just return the entity as is for now
        return CompletableFuture.completedFuture(adoptionRequest);
    }

    @PostMapping("/pets")
    public CompletableFuture<ResponseEntity<AddUpdatePetResponse>> addOrUpdatePet(@RequestBody @Valid PetRequest petRequest) {
        logger.info("Received request to add/update pet: {}", petRequest);
        return CompletableFuture.supplyAsync(() -> {
            try {
                syncPetWithExternalApi(petRequest);
            } catch (Exception e) {
                logger.error("Failed to sync pet with external API: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to sync with external Petstore API");
            }
            Pet pet = new Pet(petRequest.getPetId(), petRequest.getName(), petRequest.getCategory(), petRequest.getStatus());
            CompletableFuture<UUID> idFuture;
            if (pet.getPetId() == null || pet.getPetId().isBlank()) {
                idFuture = entityService.addItem(ENTITY_NAME_PET, ENTITY_VERSION, pet, this::processPet);
            } else {
                try {
                    UUID technicalId = UUID.fromString(pet.getPetId());
                    idFuture = entityService.updateItem(ENTITY_NAME_PET, ENTITY_VERSION, technicalId, pet);
                } catch (IllegalArgumentException ex) {
                    // If petId is invalid UUID, treat as new item
                    idFuture = entityService.addItem(ENTITY_NAME_PET, ENTITY_VERSION, pet, this::processPet);
                }
            }
            UUID technicalId = idFuture.join();
            logger.info("Pet stored with technicalId: {}", technicalId);
            return ResponseEntity.ok(new AddUpdatePetResponse(technicalId.toString(), "Pet added/updated successfully"));
        });
    }

    private void syncPetWithExternalApi(PetRequest petRequest) throws Exception {
        String url = PETSTORE_API_BASE + "/pet";
        Map<String, Object> petPayload = new HashMap<>();
        Long idVal = LongParse.parseLongOrNull(petRequest.getPetId());
        petPayload.put("id", idVal);
        petPayload.put("name", petRequest.getName());
        Map<String, Object> categoryMap = new HashMap<>();
        categoryMap.put("name", petRequest.getCategory());
        petPayload.put("category", categoryMap);
        petPayload.put("status", petRequest.getStatus());
        if (idVal == null) {
            petPayload.remove("id");
        }
        ResponseEntity<String> response;
        if (idVal == null) {
            response = restTemplate.postForEntity(new URI(url), petPayload, String.class);
        } else {
            restTemplate.put(new URI(url), petPayload);
            response = ResponseEntity.ok("{}");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Petstore API returned error");
        }
    }

    @GetMapping("/pets")
    public CompletableFuture<ResponseEntity<List<Pet>>> getPets() {
        logger.info("Retrieving all pets");
        return entityService.getItems(ENTITY_NAME_PET, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<Pet> pets = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        String technicalId = node.get("technicalId").asText();
                        String petId = technicalId;
                        String name = node.has("name") ? node.get("name").asText() : null;
                        String category = node.has("category") ? node.get("category").asText() : null;
                        String status = node.has("status") ? node.get("status").asText() : null;
                        pets.add(new Pet(petId, name, category, status));
                    }
                    logger.info("Retrieved {} pets", pets.size());
                    return ResponseEntity.ok(pets);
                });
    }

    @PostMapping("/adopt")
    public CompletableFuture<ResponseEntity<AdoptionResponse>> submitAdoptionRequest(@RequestBody @Valid AdoptionRequest adoptionRequest) {
        logger.info("Received adoption request: {}", adoptionRequest);
        if (adoptionRequest.getPetId() == null || adoptionRequest.getPetId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PetId is required");
        }
        UUID petUUID;
        try {
            petUUID = UUID.fromString(adoptionRequest.getPetId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid petId format");
        }
        return entityService.getItem(ENTITY_NAME_PET, ENTITY_VERSION, petUUID)
                .thenCompose(petNode -> {
                    if (petNode == null || petNode.isEmpty()) {
                        logger.error("Pet not found: {}", adoptionRequest.getPetId());
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found");
                    }
                    String status = petNode.has("status") ? petNode.get("status").asText() : null;
                    if (!"available".equalsIgnoreCase(status)) {
                        logger.error("Pet not available for adoption: {} status={}", adoptionRequest.getPetId(), status);
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Pet is not available for adoption");
                    }
                    adoptionRequest.setAdoptionId(UUID.randomUUID().toString());
                    adoptionRequest.setStatus("pending");
                    // Pass the workflow function processAdoptionRequestEntity here
                    return entityService.addItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionRequest, this::processAdoptionRequestEntity)
                            .thenApply(technicalId -> {
                                CompletableFuture.runAsync(() -> processAdoptionRequest(technicalId));
                                return ResponseEntity.ok(new AdoptionResponse(technicalId.toString(), "pending", "Adoption request submitted"));
                            });
                });
    }

    private void processAdoptionRequest(UUID adoptionTechnicalId) {
        logger.info("Processing adoption request async: {}", adoptionTechnicalId);
        try {
            Thread.sleep(3000);
            entityService.getItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId)
                    .thenAccept(adoptionNode -> {
                        if (adoptionNode == null || adoptionNode.isEmpty()) {
                            return;
                        }
                        AdoptionRequest request = JsonNodeToAdoptionRequest(adoptionNode);
                        request.setStatus("approved");
                        entityService.updateItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId, request).join();
                        UUID petTechnicalId = UUID.fromString(request.getPetId());
                        entityService.getItem(ENTITY_NAME_PET, ENTITY_VERSION, petTechnicalId)
                                .thenAccept(petNode -> {
                                    if (petNode == null || petNode.isEmpty()) {
                                        return;
                                    }
                                    Pet pet = JsonNodeToPet(petNode);
                                    pet.setStatus("sold");
                                    entityService.updateItem(ENTITY_NAME_PET, ENTITY_VERSION, petTechnicalId, pet).join();
                                    logger.info("Adoption request approved: {}", adoptionTechnicalId);
                                }).join();
                    }).join();
        } catch (InterruptedException e) {
            logger.error("Error processing adoption request: {}", e.getMessage(), e);
            entityService.getItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId)
                    .thenAccept(adoptionNode -> {
                        if (adoptionNode == null || adoptionNode.isEmpty()) {
                            return;
                        }
                        AdoptionRequest request = JsonNodeToAdoptionRequest(adoptionNode);
                        request.setStatus("denied");
                        entityService.updateItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId, request).join();
                    }).join();
        }
    }

    @GetMapping("/adopt/{adoptionId}")
    public CompletableFuture<ResponseEntity<AdoptionRequest>> getAdoptionStatus(@PathVariable("adoptionId") String adoptionId) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(adoptionId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid adoptionId format");
        }
        return entityService.getItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, technicalId)
                .thenApply(adoptionNode -> {
                    if (adoptionNode == null || adoptionNode.isEmpty()) {
                        logger.error("Adoption request not found: {}", adoptionId);
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adoption request not found");
                    }
                    AdoptionRequest request = JsonNodeToAdoptionRequest(adoptionNode);
                    logger.info("Returning adoption status for id {}: {}", adoptionId, request.getStatus());
                    return ResponseEntity.ok(request);
                });
    }

    @PostMapping("/pet-care-tips")
    public ResponseEntity<PetCareTipsResponse> getPetCareTips(@RequestBody @Valid PetCareTipsRequest request) {
        logger.info("Received pet care tips request for category: {}", request.getCategory());
        List<String> tips = switch (request.getCategory().toLowerCase(Locale.ROOT)) {
            case "dog" -> List.of("Walk your dog daily", "Provide fresh water", "Regular vet checkups");
            case "cat" -> List.of("Provide scratching posts", "Keep litter box clean", "Feed balanced diet");
            default -> List.of("Ensure proper habitat", "Feed appropriate food", "Regular health checks");
        };
        return ResponseEntity.ok(new PetCareTipsResponse(request.getCategory(), tips));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Error: {} - {}", ex.getStatusCode(), ex.getReason());
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", ex.getStatusCode().toString());
        errorBody.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(errorBody);
    }

    private static class LongParse {
        static Long parseLongOrNull(String val) {
            try {
                if (val == null || val.isBlank()) return null;
                return Long.parseLong(val);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    private static AdoptionRequest JsonNodeToAdoptionRequest(JsonNode node) {
        AdoptionRequest request = new AdoptionRequest();
        if (node.has("technicalId")) {
            request.setAdoptionId(node.get("technicalId").asText());
        }
        if (node.has("petId")) {
            request.setPetId(node.get("petId").asText());
        }
        if (node.has("userId")) {
            request.setUserId(node.get("userId").asText());
        }
        if (node.has("status")) {
            request.setStatus(node.get("status").asText());
        }
        return request;
    }

    private static Pet JsonNodeToPet(JsonNode node) {
        String technicalId = node.has("technicalId") ? node.get("technicalId").asText() : null;
        String name = node.has("name") ? node.get("name").asText() : null;
        String category = node.has("category") ? node.get("category").asText() : null;
        String status = node.has("status") ? node.get("status").asText() : null;
        return new Pet(technicalId, name, category, status);
    }

    @Data
    public static class PetRequest {
        private String petId;

        @NotBlank @Size(min = 1, max = 100)
        private String name;

        @NotBlank @Size(min = 1, max = 50)
        private String category;

        @NotBlank @Pattern(regexp = "available|pending|sold")
        private String status;
    }

    @Data
    public static class Pet {
        private final String petId;
        private String name;
        private String category;
        private String status;
    }

    @Data
    public static class AddUpdatePetResponse {
        private final String petId;
        private final String message;
    }

    @Data
    public static class AdoptionRequest {
        private String adoptionId;

        @NotBlank
        private String petId;

        @NotBlank
        private String userId;

        private String status;
    }

    @Data
    public static class AdoptionResponse {
        private final String adoptionId;
        private final String status;
        private final String message;
    }

    @Data
    public static class PetCareTipsRequest {
        @NotBlank @Size(min = 1, max = 50)
        private String category;
    }

    @Data
    public static class PetCareTipsResponse {
        private final String category;
        private final List<String> tips;
    }
}
```

---

### Explanation of changes:
- Added two workflow functions:
  - `private CompletableFuture<Pet> processPet(Pet pet)` — workflow for Pet entity.
  - `private CompletableFuture<AdoptionRequest> processAdoptionRequestEntity(AdoptionRequest adoptionRequest)` — workflow for AdoptionRequest entity.
- Updated calls to `entityService.addItem` for Pet and AdoptionRequest entities to include the workflow function as the fourth parameter.
- The workflow function signature matches the requirement: a function that asynchronously processes the entity before persistence and returns the entity.
- No changes were made to `updateItem` calls as per instructions, only `addItem` requires the workflow function.

This meets the requirement that `entityService.addItem` now expects the workflow function as an additional argument to process the entity asynchronously before saving.