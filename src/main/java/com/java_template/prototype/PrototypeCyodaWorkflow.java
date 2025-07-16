package com.java_template.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
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

import static com.java_template.common.config.Config.*;

@Slf4j
@Validated
@RestController
@RequestMapping(path = "/cyoda-entity")
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ENTITY_NAME_PET = "Pet";
    private static final String ENTITY_NAME_ADOPTION_REQUEST = "AdoptionRequest";
    private static final String PETSTORE_API_BASE = "https://petstore.swagger.io/v2";

    public CyodaEntityControllerPrototype(EntityService entityService) {
        this.entityService = entityService;
    }

    // Workflow function to process Pet entity before persistence.
    // Performs external Petstore API sync asynchronously.
    private CompletableFuture<ObjectNode> processPet(ObjectNode petEntity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String petId = petEntity.hasNonNull("petId") ? petEntity.get("petId").asText() : null;
                String name = petEntity.hasNonNull("name") ? petEntity.get("name").asText() : null;
                String category = petEntity.hasNonNull("category") ? petEntity.get("category").asText() : null;
                String status = petEntity.hasNonNull("status") ? petEntity.get("status").asText() : null;

                Map<String, Object> petPayload = new HashMap<>();
                Long idLong = parseLongOrNull(petId);
                if (idLong != null) {
                    petPayload.put("id", idLong);
                }
                petPayload.put("name", name);
                Map<String, Object> categoryMap = new HashMap<>();
                categoryMap.put("name", category);
                petPayload.put("category", categoryMap);
                petPayload.put("status", status);

                String url = PETSTORE_API_BASE + "/pet";

                if (idLong == null) {
                    restTemplate.postForEntity(new URI(url), petPayload, String.class);
                } else {
                    restTemplate.put(new URI(url), petPayload);
                }

                return petEntity;
            } catch (Exception e) {
                logger.error("Error syncing pet with external API: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to sync with external Petstore API", e);
            }
        });
    }

    // Workflow function to process AdoptionRequest entity before persistence.
    // Sets status to "pending" and triggers async approval or denial process.
    private CompletableFuture<ObjectNode> processAdoptionRequestEntity(ObjectNode adoptionEntity) {
        adoptionEntity.put("status", "pending");

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3000);

                if (!adoptionEntity.hasNonNull("technicalId")) {
                    logger.warn("Adoption request entity missing technicalId, cannot process approval");
                    return;
                }
                UUID adoptionTechnicalId = UUID.fromString(adoptionEntity.get("technicalId").asText());

                JsonNode adoptionNode = entityService.getItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId).join();
                if (adoptionNode == null || adoptionNode.isEmpty()) {
                    logger.warn("Adoption request not found for approval processing: {}", adoptionTechnicalId);
                    return;
                }

                ObjectNode freshAdoption = (ObjectNode) adoptionNode;

                String petId = freshAdoption.hasNonNull("petId") ? freshAdoption.get("petId").asText() : null;
                if (petId == null) {
                    logger.warn("Adoption request missing petId for approval processing: {}", adoptionTechnicalId);
                    return;
                }
                UUID petTechnicalId = UUID.fromString(petId);

                JsonNode petNode = entityService.getItem(ENTITY_NAME_PET, ENTITY_VERSION, petTechnicalId).join();
                if (petNode == null || petNode.isEmpty()) {
                    logger.warn("Pet not found for adoption approval: {}", petId);
                    freshAdoption.put("status", "denied");
                    entityService.updateItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId, freshAdoption).join();
                    return;
                }

                ObjectNode petEntity = (ObjectNode) petNode;

                if (!"available".equalsIgnoreCase(petEntity.path("status").asText(null))) {
                    freshAdoption.put("status", "denied");
                    entityService.updateItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId, freshAdoption).join();
                    return;
                }

                freshAdoption.put("status", "approved");
                entityService.updateItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId, freshAdoption).join();

                petEntity.put("status", "sold");
                entityService.updateItem(ENTITY_NAME_PET, ENTITY_VERSION, petTechnicalId, petEntity).join();

                logger.info("Adoption request {} approved and pet {} marked as sold", adoptionTechnicalId, petTechnicalId);

            } catch (Exception e) {
                logger.error("Error processing adoption request approval asynchronously", e);
                try {
                    if (adoptionEntity.hasNonNull("technicalId")) {
                        UUID adoptionTechnicalId = UUID.fromString(adoptionEntity.get("technicalId").asText());
                        JsonNode adoptionNode = entityService.getItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId).join();
                        if (adoptionNode != null && !adoptionNode.isEmpty()) {
                            ObjectNode adoptionObj = (ObjectNode) adoptionNode;
                            adoptionObj.put("status", "denied");
                            entityService.updateItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionTechnicalId, adoptionObj).join();
                        }
                    }
                } catch (Exception ex) {
                    logger.error("Failed to mark adoption request as denied after processing error", ex);
                }
            }
        });

        return CompletableFuture.completedFuture(adoptionEntity);
    }

    @PostMapping("/pets")
    public CompletableFuture<ResponseEntity<AddUpdatePetResponse>> addOrUpdatePet(@RequestBody @Valid PetRequest petRequest) {
        logger.info("Received request to add/update pet: {}", petRequest);

        ObjectNode petEntity = objectMapper.createObjectNode();
        if (petRequest.getPetId() != null && !petRequest.getPetId().isBlank()) {
            petEntity.put("petId", petRequest.getPetId());
        }
        petEntity.put("name", petRequest.getName());
        petEntity.put("category", petRequest.getCategory());
        petEntity.put("status", petRequest.getStatus());

        CompletableFuture<UUID> idFuture;

        if (!petEntity.hasNonNull("petId") || petEntity.get("petId").asText().isBlank()) {
            idFuture = entityService.addItem(ENTITY_NAME_PET, ENTITY_VERSION, petEntity, this::processPet);
        } else {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(petEntity.get("petId").asText());
            } catch (IllegalArgumentException e) {
                idFuture = entityService.addItem(ENTITY_NAME_PET, ENTITY_VERSION, petEntity, this::processPet);
                return idFuture.thenApply(id -> ResponseEntity.ok(new AddUpdatePetResponse(id.toString(), "Pet added successfully")));
            }
            idFuture = entityService.updateItem(ENTITY_NAME_PET, ENTITY_VERSION, technicalId, petEntity);
        }

        return idFuture.thenApply(id -> {
            logger.info("Pet stored with technicalId: {}", id);
            return ResponseEntity.ok(new AddUpdatePetResponse(id.toString(), "Pet added/updated successfully"));
        });
    }

    @GetMapping("/pets")
    public CompletableFuture<ResponseEntity<List<ObjectNode>>> getPets() {
        logger.info("Retrieving all pets");
        return entityService.getItems(ENTITY_NAME_PET, ENTITY_VERSION)
                .thenApply(arrayNode -> {
                    List<ObjectNode> pets = new ArrayList<>();
                    for (JsonNode node : arrayNode) {
                        if (node.isObject()) {
                            pets.add((ObjectNode) node);
                        }
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

                    ObjectNode adoptionEntity = objectMapper.createObjectNode();
                    String adoptionId = UUID.randomUUID().toString();
                    adoptionEntity.put("adoptionId", adoptionId);
                    adoptionEntity.put("petId", adoptionRequest.getPetId());
                    adoptionEntity.put("userId", adoptionRequest.getUserId());

                    return entityService.addItem(ENTITY_NAME_ADOPTION_REQUEST, ENTITY_VERSION, adoptionEntity, this::processAdoptionRequestEntity)
                            .thenApply(technicalId -> ResponseEntity.ok(
                                    new AdoptionResponse(technicalId.toString(), "pending", "Adoption request submitted")));
                });
    }

    @GetMapping("/adopt/{adoptionId}")
    public CompletableFuture<ResponseEntity<ObjectNode>> getAdoptionStatus(@PathVariable("adoptionId") String adoptionId) {
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
                    logger.info("Returning adoption status for id {}: {}", adoptionId, adoptionNode.path("status").asText());
                    return ResponseEntity.ok((ObjectNode) adoptionNode);
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

    private static Long parseLongOrNull(String val) {
        try {
            if (val == null || val.isBlank()) return null;
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            return null;
        }
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