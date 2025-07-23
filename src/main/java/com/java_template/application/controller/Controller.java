package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // ------------------ PetIngestionJob Endpoints ------------------

    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob jobRequest) throws ExecutionException, InterruptedException {
        if (jobRequest == null || jobRequest.getSource() == null || jobRequest.getSource().isBlank()) {
            logger.error("Invalid PetIngestionJob creation request: missing source");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: source");
        }

        PetIngestionJob newJob = new PetIngestionJob();
        // id and jobId will be assigned after saving - technicalId is UUID in external service
        newJob.setSource(jobRequest.getSource());
        newJob.setStatus("PENDING");
        newJob.setCreatedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "PetIngestionJob",
                ENTITY_VERSION,
                newJob
        );

        UUID technicalId = idFuture.get();

        // Set technicalId as String id for response consistency
        String idString = technicalId.toString();
        newJob.setId(idString);
        newJob.setJobId(idString);

        logger.info("Created PetIngestionJob with technicalId: {}", idString);

        processPetIngestionJob(newJob);

        return ResponseEntity.status(HttpStatus.CREATED).body(newJob);
    }

    @GetMapping("/petIngestionJob/{id}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid PetIngestionJob ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "PetIngestionJob",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode node = itemFuture.get();

        if (node == null || node.isNull() || node.isEmpty()) {
            logger.error("PetIngestionJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }

        PetIngestionJob job = nodeToPetIngestionJob(node);
        job.setId(id);
        job.setJobId(id);

        return ResponseEntity.ok(job);
    }

    // ------------------ Pet Endpoints ------------------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet petRequest) throws ExecutionException, InterruptedException {
        if (petRequest == null || !validatePetRequest(petRequest)) {
            logger.error("Invalid Pet creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required pet fields");
        }

        Pet newPet = new Pet();
        // id and petId assigned after saving
        newPet.setName(petRequest.getName());
        newPet.setCategory(petRequest.getCategory());
        newPet.setPhotoUrls(petRequest.getPhotoUrls() != null ? petRequest.getPhotoUrls() : new ArrayList<>());
        newPet.setTags(petRequest.getTags() != null ? petRequest.getTags() : new ArrayList<>());
        newPet.setStatus("NEW");

        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Pet",
                ENTITY_VERSION,
                newPet
        );

        UUID technicalId = idFuture.get();
        String idString = technicalId.toString();
        newPet.setId(idString);
        newPet.setPetId(idString);

        logger.info("Created Pet with technicalId: {}", idString);

        processPet(newPet);

        return ResponseEntity.status(HttpStatus.CREATED).body(newPet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException {
        UUID technicalId;
        try {
            technicalId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid Pet ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid ID format");
        }

        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "Pet",
                ENTITY_VERSION,
                technicalId
        );

        ObjectNode node = itemFuture.get();

        if (node == null || node.isNull() || node.isEmpty()) {
            logger.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }

        Pet pet = nodeToPet(node);
        pet.setId(id);
        pet.setPetId(id);

        return ResponseEntity.ok(pet);
    }

    // ------------------ Process Methods ------------------

    private void processPetIngestionJob(PetIngestionJob job) throws ExecutionException, InterruptedException {
        logger.info("Processing PetIngestionJob with ID: {}", job.getId());

        // 2. Update status to PROCESSING by creating new version (event-driven, so create new entity version)
        PetIngestionJob processingJob = new PetIngestionJob();
        processingJob.setSource(job.getSource());
        processingJob.setStatus("PROCESSING");
        processingJob.setCreatedAt(job.getCreatedAt());

        CompletableFuture<UUID> updateFuture = entityService.addItem(
                "PetIngestionJob",
                ENTITY_VERSION,
                processingJob
        );
        UUID processingId = updateFuture.get();
        String processingIdStr = processingId.toString();
        processingJob.setId(processingIdStr);
        processingJob.setJobId(processingIdStr);

        logger.info("PetIngestionJob {} status updated to PROCESSING", processingJob.getId());

        // 3. Fetch data from Petstore API (simulated here)
        List<Pet> fetchedPets = new ArrayList<>();

        Pet pet1 = new Pet();
        pet1.setName("Whiskers");
        pet1.setCategory("cat");
        pet1.setPhotoUrls(Arrays.asList("http://image1.jpg", "http://image2.jpg"));
        pet1.setTags(Arrays.asList("cute", "playful"));
        pet1.setStatus("NEW");

        Pet pet2 = new Pet();
        pet2.setName("Barkley");
        pet2.setCategory("dog");
        pet2.setPhotoUrls(Arrays.asList("http://dogimage1.jpg"));
        pet2.setTags(Arrays.asList("friendly", "energetic"));
        pet2.setStatus("NEW");

        fetchedPets.add(pet1);
        fetchedPets.add(pet2);

        // 4. Persist pets and process each
        List<CompletableFuture<UUID>> futures = new ArrayList<>();
        for (Pet pet : fetchedPets) {
            CompletableFuture<UUID> petIdFuture = entityService.addItem(
                    "Pet",
                    ENTITY_VERSION,
                    pet
            );
            futures.add(petIdFuture);
        }

        List<UUID> petIds = new ArrayList<>();
        for (CompletableFuture<UUID> fut : futures) {
            petIds.add(fut.get());
        }

        for (int i = 0; i < fetchedPets.size(); i++) {
            Pet pet = fetchedPets.get(i);
            UUID petId = petIds.get(i);
            String petIdStr = petId.toString();
            pet.setId(petIdStr);
            pet.setPetId(petIdStr);
            logger.info("Persisted Pet from ingestion: {}", pet.getId());
            processPet(pet);
        }

        // 5. Update job status to COMPLETED by creating new version
        PetIngestionJob completedJob = new PetIngestionJob();
        completedJob.setSource(job.getSource());
        completedJob.setStatus("COMPLETED");
        completedJob.setCreatedAt(job.getCreatedAt());

        CompletableFuture<UUID> completedFuture = entityService.addItem(
                "PetIngestionJob",
                ENTITY_VERSION,
                completedJob
        );
        UUID completedId = completedFuture.get();
        String completedIdStr = completedId.toString();
        completedJob.setId(completedIdStr);
        completedJob.setJobId(completedIdStr);

        logger.info("PetIngestionJob {} status updated to COMPLETED", completedJob.getId());
    }

    private void processPet(Pet pet) throws ExecutionException, InterruptedException {
        logger.info("Processing Pet with ID: {}", pet.getId());

        if (pet.getTags() == null || pet.getTags().isEmpty()) {
            pet.setTags(new ArrayList<>(Collections.singletonList("fun pet")));
            logger.info("Added default fun pet tag to Pet {}", pet.getId());
        }

        if ("NEW".equalsIgnoreCase(pet.getStatus())) {
            // Create new version with status AVAILABLE
            Pet updatedPet = new Pet();
            updatedPet.setName(pet.getName());
            updatedPet.setCategory(pet.getCategory());
            updatedPet.setPhotoUrls(pet.getPhotoUrls());
            updatedPet.setTags(pet.getTags());
            updatedPet.setStatus("AVAILABLE");

            CompletableFuture<UUID> updatedIdFuture = entityService.addItem(
                    "Pet",
                    ENTITY_VERSION,
                    updatedPet
            );
            UUID updatedId = updatedIdFuture.get();
            String updatedIdStr = updatedId.toString();
            updatedPet.setId(updatedIdStr);
            updatedPet.setPetId(updatedIdStr);

            logger.info("Pet {} status updated to AVAILABLE", updatedPet.getId());
        }

        // Finalize pet entity state (no further action)
    }

    // ------------------ Helper Methods ------------------

    private boolean validatePetRequest(Pet pet) {
        return pet != null
                && pet.getName() != null && !pet.getName().isBlank()
                && pet.getCategory() != null && !pet.getCategory().isBlank();
    }

    private PetIngestionJob nodeToPetIngestionJob(ObjectNode node) {
        PetIngestionJob job = new PetIngestionJob();
        if (node.has("source")) job.setSource(node.get("source").asText(null));
        if (node.has("status")) job.setStatus(node.get("status").asText(null));
        if (node.has("createdAt")) {
            try {
                job.setCreatedAt(LocalDateTime.parse(node.get("createdAt").asText()));
            } catch (Exception ignored) { }
        }
        return job;
    }

    private Pet nodeToPet(ObjectNode node) {
        Pet pet = new Pet();
        if (node.has("name")) pet.setName(node.get("name").asText(null));
        if (node.has("category")) pet.setCategory(node.get("category").asText(null));
        if (node.has("photoUrls") && node.get("photoUrls").isArray()) {
            List<String> photoUrls = new ArrayList<>();
            for (var item : node.withArray("photoUrls")) {
                photoUrls.add(item.asText());
            }
            pet.setPhotoUrls(photoUrls);
        }
        if (node.has("tags") && node.get("tags").isArray()) {
            List<String> tags = new ArrayList<>();
            for (var item : node.withArray("tags")) {
                tags.add(item.asText());
            }
            pet.setTags(tags);
        }
        if (node.has("status")) pet.setStatus(node.get("status").asText(null));
        return pet;
    }
}