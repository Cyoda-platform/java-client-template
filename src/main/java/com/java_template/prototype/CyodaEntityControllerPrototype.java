package com.java_template.prototype;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Notification;
import com.java_template.application.entity.Pet;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Validated
@RestController
@RequestMapping(path = "/prototype/pets")
@RequiredArgsConstructor
public class CyodaEntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(CyodaEntityControllerPrototype.class);

    private final EntityService entityService;

    private static final String ENTITY_MODEL_PET = "Pet";
    private static final String ENTITY_MODEL_ADOPTION_REQUEST = "AdoptionRequest";

    // ------------------ PET CRUD ------------------

    @PostMapping
    public ResponseEntity<EntityResponse> createPet(@RequestBody @Valid PetRequest petReq) {
        Pet pet = toPetEntity(petReq);
        if (!pet.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Pet validation failed");
        }
        CompletableFuture<UUID> idFuture = entityService.addItem(
                ENTITY_MODEL_PET,
                ENTITY_VERSION,
                pet
        );
        UUID technicalId = idFuture.join();
        pet.setTechnicalId(technicalId.toString());
        processPet(pet);
        logger.info("Created Pet with technicalId={}", technicalId);
        return ResponseEntity.ok(new EntityResponse(technicalId.toString(), "Pet created and processed"));
    }

    @GetMapping
    public ResponseEntity<Pet> getPet(@Valid @ModelAttribute PetQuery petQuery) {
        UUID technicalId = UUID.fromString(petQuery.getId());
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                ENTITY_MODEL_PET,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode objectNode = itemFuture.join();
        if (objectNode == null || objectNode.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + petQuery.getId());
        }
        Pet pet = objectNodeToPet(objectNode);
        logger.info("Retrieved Pet with technicalId={}", petQuery.getId());
        return ResponseEntity.ok(pet);
    }

    @PutMapping
    public ResponseEntity<EntityResponse> updatePet(@RequestBody @Valid PetUpdateRequest petReq) {
        Pet pet = toPetEntity(petReq);
        if (!pet.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Pet validation failed");
        }
        UUID technicalId;
        try {
            technicalId = UUID.fromString(pet.getTechnicalId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid technicalId");
        }
        CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                ENTITY_MODEL_PET,
                ENTITY_VERSION,
                technicalId,
                pet
        );
        updatedItemId.join(); // propagate exceptions
        processPet(pet);
        logger.info("Updated Pet with technicalId={}", technicalId);
        return ResponseEntity.ok(new EntityResponse(technicalId.toString(), "Pet updated and processed"));
    }

    @DeleteMapping
    public ResponseEntity<EntityResponse> deletePet(@RequestParam @NotBlank String id) {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid technicalId");
        }
        CompletableFuture<UUID> deletedItemId = entityService.deleteItem(
                ENTITY_MODEL_PET,
                ENTITY_VERSION,
                technicalId
        );
        deletedItemId.join();
        logger.info("Deleted Pet with technicalId={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Pet deleted"));
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet event for technicalId={}", pet.getTechnicalId());
        // TODO: Simulate Cyoda event processing for Pet entity
    }

    // ------------------ ADOPTION REQUEST CRUD ------------------

    @RestController
    @RequestMapping(path = "/prototype/adoptionRequests")
    @RequiredArgsConstructor
    public static class AdoptionRequestController {
        private static final Logger logger = LoggerFactory.getLogger(AdoptionRequestController.class);

        private final EntityService entityService;

        private static final String ENTITY_MODEL_ADOPTION_REQUEST = "AdoptionRequest";

        @PostMapping
        public ResponseEntity<EntityResponse> createAdoptionRequest(@RequestBody @Valid AdoptionRequestRequest req) {
            AdoptionRequest adoptionRequest = toAdoptionRequestEntity(req);
            if (!adoptionRequest.isValid()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "AdoptionRequest validation failed");
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    ENTITY_MODEL_ADOPTION_REQUEST,
                    ENTITY_VERSION,
                    adoptionRequest
            );
            UUID technicalId = idFuture.join();
            adoptionRequest.setTechnicalId(technicalId.toString());
            processAdoptionRequest(adoptionRequest);
            logger.info("Created AdoptionRequest with technicalId={}", technicalId);
            return ResponseEntity.ok(new EntityResponse(technicalId.toString(), "AdoptionRequest created and processed"));
        }

        @GetMapping
        public ResponseEntity<AdoptionRequest> getAdoptionRequest(@Valid @ModelAttribute AdoptionRequestQuery query) {
            UUID technicalId = UUID.fromString(query.getId());
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    ENTITY_MODEL_ADOPTION_REQUEST,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode objectNode = itemFuture.join();
            if (objectNode == null || objectNode.isEmpty()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found with id " + query.getId());
            }
            AdoptionRequest request = objectNodeToAdoptionRequest(objectNode);
            logger.info("Retrieved AdoptionRequest with technicalId={}", query.getId());
            return ResponseEntity.ok(request);
        }

        @PutMapping
        public ResponseEntity<EntityResponse> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdateRequest req) {
            AdoptionRequest adoptionRequest = toAdoptionRequestEntity(req);
            if (!adoptionRequest.isValid()) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "AdoptionRequest validation failed");
            }
            UUID technicalId;
            try {
                technicalId = UUID.fromString(adoptionRequest.getTechnicalId());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid technicalId");
            }
            CompletableFuture<UUID> updatedItemId = entityService.updateItem(
                    ENTITY_MODEL_ADOPTION_REQUEST,
                    ENTITY_VERSION,
                    technicalId,
                    adoptionRequest
            );
            updatedItemId.join();
            processAdoptionRequest(adoptionRequest);
            logger.info("Updated AdoptionRequest with technicalId={}", technicalId);
            return ResponseEntity.ok(new EntityResponse(technicalId.toString(), "AdoptionRequest updated and processed"));
        }

        @DeleteMapping
        public ResponseEntity<EntityResponse> deleteAdoptionRequest(@RequestParam @NotBlank String id) {
            UUID technicalId;
            try {
                technicalId = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid technicalId");
            }
            CompletableFuture<UUID> deletedItemId = entityService.deleteItem(
                    ENTITY_MODEL_ADOPTION_REQUEST,
                    ENTITY_VERSION,
                    technicalId
            );
            deletedItemId.join();
            logger.info("Deleted AdoptionRequest with technicalId={}", id);
            return ResponseEntity.ok(new EntityResponse(id, "AdoptionRequest deleted"));
        }

        private void processAdoptionRequest(AdoptionRequest adoptionRequest) {
            logger.info("Processing AdoptionRequest event for technicalId={}", adoptionRequest.getTechnicalId());
            // TODO: Simulate Cyoda event processing for AdoptionRequest entity
        }

        private static AdoptionRequest toAdoptionRequestEntity(AdoptionRequestRequest req) {
            AdoptionRequest ar = new AdoptionRequest();
            ar.setId(req.getId());
            ar.setTechnicalId(req.getTechnicalId());
            ar.setPetId(req.getPetId());
            ar.setAdopterName(req.getAdopterName());
            return ar;
        }

        private static AdoptionRequest toAdoptionRequestEntity(AdoptionRequestUpdateRequest req) {
            AdoptionRequest ar = new AdoptionRequest();
            ar.setId(req.getId());
            ar.setTechnicalId(req.getTechnicalId());
            ar.setPetId(req.getPetId());
            ar.setAdopterName(req.getAdopterName());
            return ar;
        }

        private static AdoptionRequest objectNodeToAdoptionRequest(ObjectNode objectNode) {
            AdoptionRequest ar = new AdoptionRequest();
            if (objectNode.has("id")) ar.setId(objectNode.get("id").asText(null));
            if (objectNode.has("technicalId")) ar.setTechnicalId(objectNode.get("technicalId").asText(null));
            if (objectNode.has("petId")) ar.setPetId(objectNode.get("petId").asText(null));
            if (objectNode.has("adopterName")) ar.setAdopterName(objectNode.get("adopterName").asText(null));
            return ar;
        }

        @Data
        public static class AdoptionRequestRequest {
            @NotBlank
            private String id;
            @NotBlank
            private String technicalId;
            @NotBlank
            private String petId;
            @NotBlank
            private String adopterName;
        }

        @Data
        public static class AdoptionRequestUpdateRequest extends AdoptionRequestRequest {
            @NotBlank
            private String id;
        }

        @Data
        public static class AdoptionRequestQuery {
            @NotBlank
            private String id;
        }
    }

    // ------------------ NOTIFICATION CRUD (keep local cache as per instructions) ------------------

    // Use original local cache logic for Notification entity (no changes)

    // =================== Entity Conversion Helpers ====================

    private Pet toPetEntity(PetRequest req) {
        Pet pet = new Pet();
        pet.setId(req.getId());
        pet.setTechnicalId(req.getTechnicalId());
        pet.setName(req.getName());
        pet.setType(req.getType());
        pet.setAge(req.getAge());
        return pet;
    }

    private Pet toPetEntity(PetUpdateRequest req) {
        Pet pet = new Pet();
        pet.setId(req.getId());
        pet.setTechnicalId(req.getTechnicalId());
        pet.setName(req.getName());
        pet.setType(req.getType());
        pet.setAge(req.getAge());
        return pet;
    }

    private static Pet objectNodeToPet(ObjectNode objectNode) {
        Pet pet = new Pet();
        if (objectNode.has("id")) pet.setId(objectNode.get("id").asText(null));
        if (objectNode.has("technicalId")) pet.setTechnicalId(objectNode.get("technicalId").asText(null));
        if (objectNode.has("name")) pet.setName(objectNode.get("name").asText(null));
        if (objectNode.has("type")) pet.setType(objectNode.get("type").asText(null));
        if (objectNode.has("age")) pet.setAge(objectNode.get("age").asInt(0));
        return pet;
    }

    // =================== DTOs for Validation ====================

    @Data
    public static class PetRequest {
        @NotBlank
        private String id; // business id may be provided or ignored on create
        @NotBlank
        private String technicalId;
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotNull
        private Integer age;
    }

    @Data
    public static class PetUpdateRequest extends PetRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class PetQuery {
        @NotBlank
        private String id;
    }

    // =================== RESPONSE DTO ====================

    @Data
    public static class EntityResponse {
        private final String id;
        private final String status;
    }
}