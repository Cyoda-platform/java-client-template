package com.java_template.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Notification;
import com.java_template.application.entity.Pet;
import com.java_template.application.service.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Validated
@RestController
@RequestMapping(path = "/prototype/entities")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // ------------------ PET CRUD ------------------

    @PostMapping("/pets")
    public ResponseEntity<EntityResponse> createPet(@RequestBody @Valid PetRequest petReq) throws ExecutionException, InterruptedException {
        Pet pet = toPetEntity(petReq);
        if (!pet.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Pet validation failed");
        }
        String id = entityService.addItem("pet", ENTITY_VERSION, pet).get().toString();
        logger.info("Created Pet with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Pet created and processed"));
    }

    @GetMapping("/pets")
    public ResponseEntity<Pet> getPet(@Valid @ModelAttribute PetQuery petQuery) throws ExecutionException, InterruptedException {
        Pet pet = entityService.findItemById("pet", ENTITY_VERSION, petQuery.getId()).get();
        if (pet == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + petQuery.getId());
        }
        logger.info("Retrieved Pet with id={}", petQuery.getId());
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pets")
    public ResponseEntity<EntityResponse> updatePet(@RequestBody @Valid PetUpdateRequest petReq) throws ExecutionException, InterruptedException {
        Pet pet = toPetEntity(petReq);
        if (!pet.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Pet validation failed");
        }
        String id = entityService.addItem("pet", ENTITY_VERSION, pet).get().toString();
        logger.info("Updated Pet with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Pet updated and processed"));
    }

    @DeleteMapping("/pets")
    public ResponseEntity<EntityResponse> deletePet(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        boolean deleted = entityService.deleteItemById("pet", ENTITY_VERSION, id).get();
        if (!deleted) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + id);
        }
        logger.info("Deleted Pet with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Pet deleted"));
    }

    // ------------------ ADOPTION REQUEST CRUD ------------------

    @PostMapping("/adoptionRequests")
    public ResponseEntity<EntityResponse> createAdoptionRequest(@RequestBody @Valid AdoptionRequestRequest req) throws ExecutionException, InterruptedException {
        AdoptionRequest adoptionRequest = toAdoptionRequestEntity(req);
        if (!adoptionRequest.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "AdoptionRequest validation failed");
        }
        String id = entityService.addItem("adoptionRequest", ENTITY_VERSION, adoptionRequest).get().toString();
        logger.info("Created AdoptionRequest with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "AdoptionRequest created and processed"));
    }

    @GetMapping("/adoptionRequests")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@Valid @ModelAttribute AdoptionRequestQuery query) throws ExecutionException, InterruptedException {
        AdoptionRequest request = entityService.findItemById("adoptionRequest", ENTITY_VERSION, query.getId()).get();
        if (request == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found with id " + query.getId());
        }
        logger.info("Retrieved AdoptionRequest with id={}", query.getId());
        return ResponseEntity.ok(request);
    }

    @PutMapping("/adoptionRequests")
    public ResponseEntity<EntityResponse> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdateRequest req) throws ExecutionException, InterruptedException {
        AdoptionRequest adoptionRequest = toAdoptionRequestEntity(req);
        if (!adoptionRequest.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "AdoptionRequest validation failed");
        }
        String id = entityService.addItem("adoptionRequest", ENTITY_VERSION, adoptionRequest).get().toString();
        logger.info("Updated AdoptionRequest with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "AdoptionRequest updated and processed"));
    }

    @DeleteMapping("/adoptionRequests")
    public ResponseEntity<EntityResponse> deleteAdoptionRequest(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        boolean deleted = entityService.deleteItemById("adoptionRequest", ENTITY_VERSION, id).get();
        if (!deleted) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found with id " + id);
        }
        logger.info("Deleted AdoptionRequest with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "AdoptionRequest deleted"));
    }

    // ------------------ NOTIFICATION CRUD ------------------

    @PostMapping("/notifications")
    public ResponseEntity<EntityResponse> createNotification(@RequestBody @Valid NotificationRequest req) throws ExecutionException, InterruptedException {
        Notification notification = toNotificationEntity(req);
        if (!notification.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Notification validation failed");
        }
        String id = entityService.addItem("notification", ENTITY_VERSION, notification).get().toString();
        logger.info("Created Notification with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Notification created and processed"));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Notification> getNotification(@Valid @ModelAttribute NotificationQuery query) throws ExecutionException, InterruptedException {
        Notification notification = entityService.findItemById("notification", ENTITY_VERSION, query.getId()).get();
        if (notification == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found with id " + query.getId());
        }
        logger.info("Retrieved Notification with id={}", query.getId());
        return ResponseEntity.ok(notification);
    }

    @PutMapping("/notifications")
    public ResponseEntity<EntityResponse> updateNotification(@RequestBody @Valid NotificationUpdateRequest req) throws ExecutionException, InterruptedException {
        Notification notification = toNotificationEntity(req);
        if (!notification.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Notification validation failed");
        }
        String id = entityService.addItem("notification", ENTITY_VERSION, notification).get().toString();
        logger.info("Updated Notification with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Notification updated and processed"));
    }

    @DeleteMapping("/notifications")
    public ResponseEntity<EntityResponse> deleteNotification(@RequestParam @NotBlank String id) throws ExecutionException, InterruptedException {
        boolean deleted = entityService.deleteItemById("notification", ENTITY_VERSION, id).get();
        if (!deleted) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found with id " + id);
        }
        logger.info("Deleted Notification with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Notification deleted"));
    }

    // =================== DTOs for Validation ====================

    public static class PetRequest {
        @NotBlank
        private String id;
        @NotBlank
        private String technicalId;
        @NotBlank
        private String name;
        @NotBlank
        private String type;
        @NotBlank
        private Integer age;

        // getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTechnicalId() { return technicalId; }
        public void setTechnicalId(String technicalId) { this.technicalId = technicalId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
    }

    public static class PetUpdateRequest extends PetRequest {
        @NotBlank
        private String id;

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) { this.id = id; }
    }

    public static class PetQuery {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class AdoptionRequestRequest {
        @NotBlank
        private String id;
        @NotBlank
        private String technicalId;
        @NotBlank
        private String petId;
        @NotBlank
        private String adopterName;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTechnicalId() { return technicalId; }
        public void setTechnicalId(String technicalId) { this.technicalId = technicalId; }
        public String getPetId() { return petId; }
        public void setPetId(String petId) { this.petId = petId; }
        public String getAdopterName() { return adopterName; }
        public void setAdopterName(String adopterName) { this.adopterName = adopterName; }
    }

    public static class AdoptionRequestUpdateRequest extends AdoptionRequestRequest {
        @NotBlank
        private String id;

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) { this.id = id; }
    }

    public static class AdoptionRequestQuery {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class NotificationRequest {
        @NotBlank
        private String id;
        @NotBlank
        private String technicalId;
        @NotBlank
        private String message;
        @NotBlank
        private String recipientId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTechnicalId() { return technicalId; }
        public void setTechnicalId(String technicalId) { this.technicalId = technicalId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getRecipientId() { return recipientId; }
        public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    }

    public static class NotificationUpdateRequest extends NotificationRequest {
        @NotBlank
        private String id;

        @Override
        public String getId() { return id; }
        @Override
        public void setId(String id) { this.id = id; }
    }

    public static class NotificationQuery {
        @NotBlank
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

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

    private AdoptionRequest toAdoptionRequestEntity(AdoptionRequestRequest req) {
        AdoptionRequest ar = new AdoptionRequest();
        ar.setId(req.getId());
        ar.setTechnicalId(req.getTechnicalId());
        ar.setPetId(req.getPetId());
        ar.setAdopterName(req.getAdopterName());
        return ar;
    }

    private AdoptionRequest toAdoptionRequestEntity(AdoptionRequestUpdateRequest req) {
        AdoptionRequest ar = new AdoptionRequest();
        ar.setId(req.getId());
        ar.setTechnicalId(req.getTechnicalId());
        ar.setPetId(req.getPetId());
        ar.setAdopterName(req.getAdopterName());
        return ar;
    }

    private Notification toNotificationEntity(NotificationRequest req) {
        Notification n = new Notification();
        n.setId(req.getId());
        n.setTechnicalId(req.getTechnicalId());
        n.setMessage(req.getMessage());
        n.setRecipientId(req.getRecipientId());
        return n;
    }

    private Notification toNotificationEntity(NotificationUpdateRequest req) {
        Notification n = new Notification();
        n.setId(req.getId());
        n.setTechnicalId(req.getTechnicalId());
        n.setMessage(req.getMessage());
        n.setRecipientId(req.getRecipientId());
        return n;
    }

    // =================== RESPONSE DTO ====================

    public static class EntityResponse {
        private final String id;
        private final String status;

        public EntityResponse(String id, String status) {
            this.id = id;
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }
    }
}