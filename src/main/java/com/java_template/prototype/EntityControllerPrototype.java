package com.java_template.prototype;

import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Notification;
import com.java_template.application.entity.Pet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Validated
@RestController
@RequestMapping(path = "/prototype/entities")
public class EntityControllerPrototype {

    private static final Logger logger = LoggerFactory.getLogger(EntityControllerPrototype.class);

    // Caches for entities
    private final ConcurrentHashMap<String, List<Pet>> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<AdoptionRequest>> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, List<Notification>> notificationCache = new ConcurrentHashMap<>();
    private final AtomicLong notificationIdCounter = new AtomicLong(1);

    // ------------------ PET CRUD ------------------

    @PostMapping("/pets")
    public ResponseEntity<EntityResponse> createPet(@RequestBody @Valid PetRequest petReq) {
        Pet pet = toPetEntity(petReq);
        if (!pet.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Pet validation failed");
        }
        String id = addPet(pet);
        logger.info("Created Pet with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Pet created and processed"));
    }

    @GetMapping("/pets")
    // Using @ModelAttribute to bind query params for GET; workaround since GET cannot have @RequestBody
    public ResponseEntity<Pet> getPet(@Valid @ModelAttribute PetQuery petQuery) {
        Pet pet = getPetById(petQuery.getId());
        if (pet == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + petQuery.getId());
        }
        logger.info("Retrieved Pet with id={}", petQuery.getId());
        return ResponseEntity.ok(pet);
    }

    @PutMapping("/pets")
    public ResponseEntity<EntityResponse> updatePet(@RequestBody @Valid PetUpdateRequest petReq) {
        Pet pet = toPetEntity(petReq);
        if (!pet.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Pet validation failed");
        }
        boolean updated = updatePetInCache(pet);
        if (!updated) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + pet.getId());
        }
        processPet(pet);
        logger.info("Updated Pet with id={}", pet.getId());
        return ResponseEntity.ok(new EntityResponse(pet.getId(), "Pet updated and processed"));
    }

    @DeleteMapping("/pets")
    public ResponseEntity<EntityResponse> deletePet(@RequestParam @NotBlank String id) {
        boolean deleted = deletePetFromCache(id);
        if (!deleted) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found with id " + id);
        }
        logger.info("Deleted Pet with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Pet deleted"));
    }

    // ------------------ ADOPTION REQUEST CRUD ------------------

    @PostMapping("/adoptionRequests")
    public ResponseEntity<EntityResponse> createAdoptionRequest(@RequestBody @Valid AdoptionRequestRequest req) {
        AdoptionRequest adoptionRequest = toAdoptionRequestEntity(req);
        if (!adoptionRequest.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "AdoptionRequest validation failed");
        }
        String id = addAdoptionRequest(adoptionRequest);
        logger.info("Created AdoptionRequest with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "AdoptionRequest created and processed"));
    }

    @GetMapping("/adoptionRequests")
    public ResponseEntity<AdoptionRequest> getAdoptionRequest(@Valid @ModelAttribute AdoptionRequestQuery query) {
        AdoptionRequest request = getAdoptionRequestById(query.getId());
        if (request == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found with id " + query.getId());
        }
        logger.info("Retrieved AdoptionRequest with id={}", query.getId());
        return ResponseEntity.ok(request);
    }

    @PutMapping("/adoptionRequests")
    public ResponseEntity<EntityResponse> updateAdoptionRequest(@RequestBody @Valid AdoptionRequestUpdateRequest req) {
        AdoptionRequest adoptionRequest = toAdoptionRequestEntity(req);
        if (!adoptionRequest.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "AdoptionRequest validation failed");
        }
        boolean updated = updateAdoptionRequestInCache(adoptionRequest);
        if (!updated) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found with id " + adoptionRequest.getId());
        }
        processAdoptionRequest(adoptionRequest);
        logger.info("Updated AdoptionRequest with id={}", adoptionRequest.getId());
        return ResponseEntity.ok(new EntityResponse(adoptionRequest.getId(), "AdoptionRequest updated and processed"));
    }

    @DeleteMapping("/adoptionRequests")
    public ResponseEntity<EntityResponse> deleteAdoptionRequest(@RequestParam @NotBlank String id) {
        boolean deleted = deleteAdoptionRequestFromCache(id);
        if (!deleted) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "AdoptionRequest not found with id " + id);
        }
        logger.info("Deleted AdoptionRequest with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "AdoptionRequest deleted"));
    }

    // ------------------ NOTIFICATION CRUD ------------------

    @PostMapping("/notifications")
    public ResponseEntity<EntityResponse> createNotification(@RequestBody @Valid NotificationRequest req) {
        Notification notification = toNotificationEntity(req);
        if (!notification.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Notification validation failed");
        }
        String id = addNotification(notification);
        logger.info("Created Notification with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Notification created and processed"));
    }

    @GetMapping("/notifications")
    public ResponseEntity<Notification> getNotification(@Valid @ModelAttribute NotificationQuery query) {
        Notification notification = getNotificationById(query.getId());
        if (notification == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found with id " + query.getId());
        }
        logger.info("Retrieved Notification with id={}", query.getId());
        return ResponseEntity.ok(notification);
    }

    @PutMapping("/notifications")
    public ResponseEntity<EntityResponse> updateNotification(@RequestBody @Valid NotificationUpdateRequest req) {
        Notification notification = toNotificationEntity(req);
        if (!notification.isValid()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Notification validation failed");
        }
        boolean updated = updateNotificationInCache(notification);
        if (!updated) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found with id " + notification.getId());
        }
        processNotification(notification);
        logger.info("Updated Notification with id={}", notification.getId());
        return ResponseEntity.ok(new EntityResponse(notification.getId(), "Notification updated and processed"));
    }

    @DeleteMapping("/notifications")
    public ResponseEntity<EntityResponse> deleteNotification(@RequestParam @NotBlank String id) {
        boolean deleted = deleteNotificationFromCache(id);
        if (!deleted) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Notification not found with id " + id);
        }
        logger.info("Deleted Notification with id={}", id);
        return ResponseEntity.ok(new EntityResponse(id, "Notification deleted"));
    }

    // =================== PRIVATE HELPER METHODS ====================

    // --- Pet methods ---

    private String addPet(Pet pet) {
        String id = String.valueOf(petIdCounter.getAndIncrement());
        pet.setId(id);
        petCache.computeIfAbsent("pets", k -> Collections.synchronizedList(new ArrayList<>())).add(pet);
        processPet(pet);
        return id;
    }

    private Pet getPetById(String id) {
        List<Pet> pets = petCache.get("pets");
        if (pets == null) return null;
        synchronized (pets) {
            return pets.stream().filter(p -> id.equals(p.getId())).findFirst().orElse(null);
        }
    }

    private boolean updatePetInCache(Pet pet) {
        List<Pet> pets = petCache.get("pets");
        if (pets == null) return false;
        synchronized (pets) {
            for (int i = 0; i < pets.size(); i++) {
                if (pets.get(i).getId().equals(pet.getId())) {
                    pets.set(i, pet);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean deletePetFromCache(String id) {
        List<Pet> pets = petCache.get("pets");
        if (pets == null) return false;
        synchronized (pets) {
            return pets.removeIf(p -> id.equals(p.getId()));
        }
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet event for id={}", pet.getId());
        // TODO: Simulate Cyoda event processing for Pet entity
    }

    // --- AdoptionRequest methods ---

    private String addAdoptionRequest(AdoptionRequest adoptionRequest) {
        String id = String.valueOf(adoptionRequestIdCounter.getAndIncrement());
        adoptionRequest.setId(id);
        adoptionRequestCache.computeIfAbsent("adoptionRequests", k -> Collections.synchronizedList(new ArrayList<>())).add(adoptionRequest);
        processAdoptionRequest(adoptionRequest);
        return id;
    }

    private AdoptionRequest getAdoptionRequestById(String id) {
        List<AdoptionRequest> requests = adoptionRequestCache.get("adoptionRequests");
        if (requests == null) return null;
        synchronized (requests) {
            return requests.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
        }
    }

    private boolean updateAdoptionRequestInCache(AdoptionRequest adoptionRequest) {
        List<AdoptionRequest> requests = adoptionRequestCache.get("adoptionRequests");
        if (requests == null) return false;
        synchronized (requests) {
            for (int i = 0; i < requests.size(); i++) {
                if (requests.get(i).getId().equals(adoptionRequest.getId())) {
                    requests.set(i, adoptionRequest);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean deleteAdoptionRequestFromCache(String id) {
        List<AdoptionRequest> requests = adoptionRequestCache.get("adoptionRequests");
        if (requests == null) return false;
        synchronized (requests) {
            return requests.removeIf(r -> id.equals(r.getId()));
        }
    }

    private void processAdoptionRequest(AdoptionRequest adoptionRequest) {
        logger.info("Processing AdoptionRequest event for id={}", adoptionRequest.getId());
        // TODO: Simulate Cyoda event processing for AdoptionRequest entity
    }

    // --- Notification methods ---

    private String addNotification(Notification notification) {
        String id = String.valueOf(notificationIdCounter.getAndIncrement());
        notification.setId(id);
        notificationCache.computeIfAbsent("notifications", k -> Collections.synchronizedList(new ArrayList<>())).add(notification);
        processNotification(notification);
        return id;
    }

    private Notification getNotificationById(String id) {
        List<Notification> notifications = notificationCache.get("notifications");
        if (notifications == null) return null;
        synchronized (notifications) {
            return notifications.stream().filter(n -> id.equals(n.getId())).findFirst().orElse(null);
        }
    }

    private boolean updateNotificationInCache(Notification notification) {
        List<Notification> notifications = notificationCache.get("notifications");
        if (notifications == null) return false;
        synchronized (notifications) {
            for (int i = 0; i < notifications.size(); i++) {
                if (notifications.get(i).getId().equals(notification.getId())) {
                    notifications.set(i, notification);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean deleteNotificationFromCache(String id) {
        List<Notification> notifications = notificationCache.get("notifications");
        if (notifications == null) return false;
        synchronized (notifications) {
            return notifications.removeIf(n -> id.equals(n.getId()));
        }
    }

    private void processNotification(Notification notification) {
        logger.info("Processing Notification event for id={}", notification.getId());
        // TODO: Simulate Cyoda event processing for Notification entity
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
        // primitives or String only, no nested objects
    }

    @Data
    public static class PetUpdateRequest extends PetRequest {
        // id must be present for update
        @NotBlank
        private String id;
    }

    @Data
    public static class PetQuery {
        @NotBlank
        private String id;
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
        // other business fields as String/primitives
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

    @Data
    public static class NotificationRequest {
        @NotBlank
        private String id;
        @NotBlank
        private String technicalId;
        @NotBlank
        private String message;
        @NotBlank
        private String recipientId;
    }

    @Data
    public static class NotificationUpdateRequest extends NotificationRequest {
        @NotBlank
        private String id;
    }

    @Data
    public static class NotificationQuery {
        @NotBlank
        private String id;
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

    @Data
    public static class EntityResponse {
        private final String id;
        private final String status;
    }
}