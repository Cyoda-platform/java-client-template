package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID generator for PetJob
    private final ConcurrentHashMap<String, PetJob> petJobCache = new ConcurrentHashMap<>();
    private final AtomicLong petJobIdCounter = new AtomicLong(1);

    // Cache and ID generator for Pet
    private final ConcurrentHashMap<String, Pet> petCache = new ConcurrentHashMap<>();
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // Cache and ID generator for AdoptionRequest
    private final ConcurrentHashMap<String, AdoptionRequest> adoptionRequestCache = new ConcurrentHashMap<>();
    private final AtomicLong adoptionRequestIdCounter = new AtomicLong(1);

    // ------------------- PETJOB ENDPOINTS -------------------

    @PostMapping("/petJob")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null || petJob.getRequestType() == null || petJob.getRequestType().isBlank()) {
            log.error("Invalid PetJob creation request: Missing requestType");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid requestType");
        }

        String newId = "petJob-" + petJobIdCounter.getAndIncrement();
        petJob.setId(newId);
        petJob.setStatus(PetJob.StatusEnum.PENDING);
        petJob.setCreatedAt(java.time.LocalDateTime.now());
        petJobCache.put(newId, petJob);

        processPetJob(petJob);

        log.info("Created PetJob with ID: {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(petJob);
    }

    @GetMapping("/petJob/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) {
        PetJob petJob = petJobCache.get(id);
        if (petJob == null) {
            log.error("PetJob not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetJob not found");
        }
        return ResponseEntity.ok(petJob);
    }

    // ------------------- PET ENDPOINTS -------------------

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        Pet pet = petCache.get(id);
        if (pet == null) {
            log.error("Pet not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        return ResponseEntity.ok(pet);
    }

    // ------------------- ADOPTIONREQUEST ENDPOINTS -------------------

    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) {
        if (adoptionRequest == null 
                || adoptionRequest.getPetId() == null 
                || adoptionRequest.getRequesterName() == null || adoptionRequest.getRequesterName().isBlank()
                || adoptionRequest.getContactInfo() == null || adoptionRequest.getContactInfo().isBlank()) {
            log.error("Invalid AdoptionRequest creation request: Missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required fields");
        }

        // Validate Pet existence and availability
        Pet pet = petCache.get(String.valueOf(adoptionRequest.getPetId()));
        if (pet == null) {
            log.error("Pet not found for AdoptionRequest with petId: {}", adoptionRequest.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Referenced Pet does not exist");
        }
        if (pet.getStatus() != Pet.StatusEnum.AVAILABLE) {
            log.error("Pet with ID {} is not available for adoption", adoptionRequest.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet is not available for adoption");
        }

        String newId = "adoptReq-" + adoptionRequestIdCounter.getAndIncrement();
        adoptionRequest.setId(newId);
        adoptionRequest.setStatus(AdoptionRequest.StatusEnum.REQUESTED);
        adoptionRequest.setRequestedAt(java.time.LocalDateTime.now());
        adoptionRequestCache.put(newId, adoptionRequest);

        processAdoptionRequest(adoptionRequest);

        log.info("Created AdoptionRequest with ID: {}", newId);
        return ResponseEntity.status(HttpStatus.CREATED).body(adoptionRequest);
    }

    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) {
        AdoptionRequest adoptionRequest = adoptionRequestCache.get(id);
        if (adoptionRequest == null) {
            log.error("AdoptionRequest not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        return ResponseEntity.ok(adoptionRequest);
    }

    // ------------------- PROCESS METHODS -------------------

    private void processPetJob(PetJob petJob) {
        log.info("Processing PetJob with ID: {}", petJob.getId());

        try {
            petJob.setStatus(PetJob.StatusEnum.PROCESSING);

            // Simulate calling Petstore API to fetch pets
            List<Pet> fetchedPets = fetchPetsFromPetstore(petJob.getRequestType(), petJob.getPetType());

            // Persist pets into cache - use new IDs for Pet entities
            for (Pet pet : fetchedPets) {
                String petId = String.valueOf(petIdCounter.getAndIncrement());
                pet.setId(Long.parseLong(petId));
                petCache.put(petId, pet);
            }

            petJob.setResultCount(fetchedPets.size());
            petJob.setStatus(PetJob.StatusEnum.COMPLETED);

            log.info("PetJob {} completed successfully with {} pets fetched", petJob.getId(), fetchedPets.size());

        } catch (Exception e) {
            log.error("Error processing PetJob {}: {}", petJob.getId(), e.getMessage());
            petJob.setStatus(PetJob.StatusEnum.FAILED);
        }
    }

    private List<Pet> fetchPetsFromPetstore(String requestType, String petType) {
        // For prototype, simulate data retrieval with static or sample data
        List<Pet> pets = new ArrayList<>();

        // Example pets
        Pet cat = new Pet();
        cat.setId(petIdCounter.getAndIncrement());
        cat.setName("Whiskers");
        cat.setCategory("cat");
        cat.setPhotoUrls(List.of("http://example.com/cat1.jpg"));
        cat.setTags(List.of("playful", "indoor"));
        cat.setStatus(Pet.StatusEnum.AVAILABLE);

        Pet dog = new Pet();
        dog.setId(petIdCounter.getAndIncrement());
        dog.setName("Rex");
        dog.setCategory("dog");
        dog.setPhotoUrls(List.of("http://example.com/dog1.jpg"));
        dog.setTags(List.of("friendly", "outdoor"));
        dog.setStatus(Pet.StatusEnum.AVAILABLE);

        if ("FETCH_ALL".equalsIgnoreCase(requestType)) {
            if (petType == null || petType.isBlank()) {
                pets.add(cat);
                pets.add(dog);
            } else if ("cat".equalsIgnoreCase(petType)) {
                pets.add(cat);
            } else if ("dog".equalsIgnoreCase(petType)) {
                pets.add(dog);
            }
        } else if ("FETCH_BY_TYPE".equalsIgnoreCase(requestType) && petType != null && !petType.isBlank()) {
            if ("cat".equalsIgnoreCase(petType)) {
                pets.add(cat);
            } else if ("dog".equalsIgnoreCase(petType)) {
                pets.add(dog);
            }
        }

        return pets;
    }

    private void processAdoptionRequest(AdoptionRequest adoptionRequest) {
        log.info("Processing AdoptionRequest with ID: {}", adoptionRequest.getId());

        Pet pet = petCache.get(String.valueOf(adoptionRequest.getPetId()));
        if (pet == null) {
            log.error("AdoptionRequest processing failed: Pet not found with ID: {}", adoptionRequest.getPetId());
            return;
        }

        if (pet.getStatus() != Pet.StatusEnum.AVAILABLE) {
            log.error("AdoptionRequest processing failed: Pet with ID {} not available", pet.getId());
            return;
        }

        // Update pet status to PENDING_ADOPTION
        Pet updatedPet = new Pet();
        updatedPet.setId(pet.getId());
        updatedPet.setTechnicalId(pet.getTechnicalId());
        updatedPet.setName(pet.getName());
        updatedPet.setCategory(pet.getCategory());
        updatedPet.setPhotoUrls(pet.getPhotoUrls());
        updatedPet.setTags(pet.getTags());
        updatedPet.setStatus(Pet.StatusEnum.PENDING_ADOPTION);
        petCache.put(String.valueOf(pet.getId()), updatedPet);

        // AdoptionRequest status remains REQUESTED awaiting approval
        log.info("AdoptionRequest {} processed: Pet {} status updated to PENDING_ADOPTION", adoptionRequest.getId(), pet.getId());
    }
}