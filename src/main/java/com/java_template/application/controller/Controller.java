package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetRegistrationJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entity")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PET_REGISTRATION_JOB_MODEL = "PetRegistrationJob";
    private static final String PET_MODEL = "Pet";

    // POST /entity/petRegistrationJob
    @PostMapping("/petRegistrationJob")
    public ResponseEntity<?> createPetRegistrationJob(@RequestBody PetRegistrationJob job) throws ExecutionException, InterruptedException {
        if (job.getSource() == null || job.getSource().isBlank()) {
            log.error("PetRegistrationJob creation failed: source is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: source");
        }
        job.setStatus("PENDING");
        job.setCreatedAt(new Date());
        // Add item via entityService (immutable, creates new version)
        CompletableFuture<UUID> idFuture = entityService.addItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        log.info("Created PetRegistrationJob with technicalId: {}", technicalId);
        // Retrieve full stored object with technicalId
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, technicalId);
        ObjectNode jobNode = jobFuture.get();
        // Convert ObjectNode back to PetRegistrationJob to process
        PetRegistrationJob savedJob = JsonUtil.convert(jobNode, PetRegistrationJob.class);
        processPetRegistrationJob(savedJob);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedJob);
    }

    // GET /entity/petRegistrationJob/{id}
    @GetMapping("/petRegistrationJob/{id}")
    public ResponseEntity<?> getPetRegistrationJob(@PathVariable UUID id) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, id);
        ObjectNode jobNode = jobFuture.get();
        if (jobNode == null || jobNode.isEmpty()) {
            log.error("PetRegistrationJob not found: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetRegistrationJob not found");
        }
        PetRegistrationJob job = JsonUtil.convert(jobNode, PetRegistrationJob.class);
        return ResponseEntity.ok(job);
    }

    // POST /entity/pet
    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet.getName() == null || pet.getName().isBlank()) {
            log.error("Pet creation failed: name is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: name");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet creation failed: category is missing");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing required field: category");
        }
        pet.setStatus(pet.getStatus() != null && !pet.getStatus().isBlank() ? pet.getStatus() : "available");
        CompletableFuture<UUID> idFuture = entityService.addItem(PET_MODEL, ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        log.info("Created Pet with technicalId: {}", technicalId);
        CompletableFuture<ObjectNode> petFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, technicalId);
        ObjectNode petNode = petFuture.get();
        Pet savedPet = JsonUtil.convert(petNode, Pet.class);
        processPet(savedPet);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedPet);
    }

    // GET /entity/pet/{petId}
    @GetMapping("/pet/{petId}")
    public ResponseEntity<?> getPet(@PathVariable UUID petId) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> petFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, petId);
        ObjectNode petNode = petFuture.get();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Pet not found: {}", petId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = JsonUtil.convert(petNode, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // GET /entity/pet
    @GetMapping("/pet")
    public ResponseEntity<?> listPets(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String category) throws ExecutionException, InterruptedException {
        SearchConditionRequest condition;
        if ((status == null || status.isBlank()) && (category == null || category.isBlank())) {
            condition = null;
        } else if (status != null && !status.isBlank() && (category == null || category.isBlank())) {
            condition = SearchConditionRequest.group("AND",
                    Condition.of("$.status", "IEQUALS", status));
        } else if ((status == null || status.isBlank()) && category != null && !category.isBlank()) {
            condition = SearchConditionRequest.group("AND",
                    Condition.of("$.category", "IEQUALS", category));
        } else {
            condition = SearchConditionRequest.group("AND",
                    Condition.of("$.status", "IEQUALS", status),
                    Condition.of("$.category", "IEQUALS", category));
        }

        CompletableFuture<ArrayNode> petsFuture;
        if (condition == null) {
            petsFuture = entityService.getItems(PET_MODEL, ENTITY_VERSION);
        } else {
            petsFuture = entityService.getItemsByCondition(PET_MODEL, ENTITY_VERSION, condition, true);
        }
        ArrayNode petsArray = petsFuture.get();
        List<Pet> pets = new ArrayList<>();
        if (petsArray != null) {
            for (int i = 0; i < petsArray.size(); i++) {
                ObjectNode petNode = (ObjectNode) petsArray.get(i);
                Pet pet = JsonUtil.convert(petNode, Pet.class);
                pets.add(pet);
            }
        }
        return ResponseEntity.ok(pets);
    }

    private void processPetRegistrationJob(PetRegistrationJob job) {
        log.info("Processing PetRegistrationJob with technicalId: {}", job.getTechnicalId());
        try {
            // Update status to PROCESSING by creating new version (immutable update)
            job.setStatus("PROCESSING");
            entityService.addItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, job).get();

            // Validate source connectivity (simulate)
            if (!"PetstoreAPI".equalsIgnoreCase(job.getSource())) {
                log.error("Unsupported source: {}", job.getSource());
                job.setStatus("FAILED");
                entityService.addItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, job).get();
                return;
            }
            // Simulate fetching pets from Petstore API
            List<Pet> fetchedPets = new ArrayList<>();
            Pet samplePet = new Pet();
            samplePet.setName("Whiskers");
            samplePet.setCategory("cat");
            samplePet.setPhotoUrls(List.of("http://example.com/whiskers.jpg"));
            samplePet.setTags(List.of("fluffy", "gray"));
            samplePet.setStatus("available");
            fetchedPets.add(samplePet);

            // Save new Pet entities immutably
            for (Pet pet : fetchedPets) {
                // Check existence by searching with pet name and category ignoring case
                SearchConditionRequest searchCondition = SearchConditionRequest.group("AND",
                        Condition.of("$.name", "IEQUALS", pet.getName()),
                        Condition.of("$.category", "IEQUALS", pet.getCategory()));
                ArrayNode existingPets = entityService.getItemsByCondition(PET_MODEL, ENTITY_VERSION, searchCondition, true).get();
                if (existingPets == null || existingPets.size() == 0) {
                    CompletableFuture<UUID> petIdFuture = entityService.addItem(PET_MODEL, ENTITY_VERSION, pet);
                    UUID petId = petIdFuture.get();
                    CompletableFuture<ObjectNode> petNodeFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, petId);
                    Pet savedPet = JsonUtil.convert(petNodeFuture.get(), Pet.class);
                    processPet(savedPet);
                    log.info("Imported Pet with technicalId: {}", petId);
                } else {
                    log.info("Pet with name '{}' and category '{}' already exists. Skipping import.", pet.getName(), pet.getCategory());
                }
            }
            job.setStatus("COMPLETED");
            entityService.addItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, job).get();
        } catch (Exception e) {
            log.error("Error processing PetRegistrationJob with technicalId {}: {}", job.getTechnicalId(), e.getMessage());
            try {
                job.setStatus("FAILED");
                entityService.addItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, job).get();
            } catch (Exception ignored) {
            }
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        if (pet.getName() == null || pet.getName().isBlank() ||
                pet.getCategory() == null || pet.getCategory().isBlank()) {
            log.error("Pet validation failed for technicalId {}: Missing name or category", pet.getTechnicalId());
            return;
        }
        if (pet.getTags() != null) {
            List<String> normalizedTags = new ArrayList<>();
            for (String tag : pet.getTags()) {
                if (tag != null) {
                    normalizedTags.add(tag.toLowerCase());
                }
            }
            pet.setTags(normalizedTags);
        }
        if (pet.getPhotoUrls() != null) {
            List<String> validUrls = new ArrayList<>();
            for (String url : pet.getPhotoUrls()) {
                if (url != null && url.startsWith("http")) {
                    validUrls.add(url);
                } else {
                    log.warn("Invalid photo URL skipped for Pet technicalId {}: {}", pet.getTechnicalId(), url);
                }
            }
            pet.setPhotoUrls(validUrls);
        }
        log.info("Pet with technicalId {} processed successfully", pet.getTechnicalId());
    }

    // Utility class for JSON conversions (ObjectNode <-> Java POJO)
    private static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convert(ObjectNode node, Class<T> clazz) {
            try {
                return mapper.treeToValue(node, clazz);
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert ObjectNode to " + clazz.getSimpleName(), e);
            }
        }
    }
}