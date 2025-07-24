package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // Local cache and counter only for Pet entity ingestion event processing (for minor entities or mock data)
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // ------ PetIngestionJob endpoints ------

    @PostMapping("/petIngestionJob")
    public ResponseEntity<?> createPetIngestionJob(@RequestBody PetIngestionJob job) throws ExecutionException, InterruptedException {
        if (job.getSource() == null || job.getSource().isBlank()) {
            logger.error("PetIngestionJob creation failed: source is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Source URL is required");
        }
        job.setStatus("PENDING");
        job.setCreatedAt(new Date());

        // Add PetIngestionJob via entityService
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "PetIngestionJob",
                ENTITY_VERSION,
                job
        );
        UUID technicalId = idFuture.get();
        job.setTechnicalId(technicalId);

        logger.info("Created PetIngestionJob with technicalId: {}", technicalId);

        // Trigger processing event
        processPetIngestionJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/petIngestionJob/{technicalId}")
    public ResponseEntity<?> getPetIngestionJob(@PathVariable UUID technicalId) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "PetIngestionJob",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode objNode = itemFuture.get();
        if (objNode == null || objNode.isEmpty()) {
            logger.error("PetIngestionJob with technicalId {} not found", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetIngestionJob not found");
        }
        // Convert ObjectNode to PetIngestionJob
        PetIngestionJob job = JsonUtils.convertValue(objNode, PetIngestionJob.class);
        return ResponseEntity.ok(job);
    }

    // ------ Pet endpoints ------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet.getName() == null || pet.getName().isBlank()) {
            logger.error("Pet creation failed: name is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet name is required");
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet creation failed: category is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet category is required");
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            logger.error("Pet creation failed: status is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Pet status is required");
        }

        // Add Pet via entityService
        CompletableFuture<UUID> idFuture = entityService.addItem(
                "Pet",
                ENTITY_VERSION,
                pet
        );
        UUID technicalId = idFuture.get();
        pet.setTechnicalId(technicalId);

        logger.info("Created Pet with technicalId: {}", technicalId);

        // Trigger processing event
        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pet/{technicalId}")
    public ResponseEntity<?> getPet(@PathVariable UUID technicalId) throws ExecutionException, InterruptedException {
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                "Pet",
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode objNode = itemFuture.get();
        if (objNode == null || objNode.isEmpty()) {
            logger.error("Pet with technicalId {} not found", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = JsonUtils.convertValue(objNode, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @GetMapping("/pets")
    public ResponseEntity<?> getPets(@RequestParam(required = false) String category,
                                     @RequestParam(required = false) String status) throws ExecutionException, InterruptedException {
        List<Condition> conditions = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            conditions.add(Condition.of("$.category", "IEQUALS", category));
        }
        if (status != null && !status.isBlank()) {
            conditions.add(Condition.of("$.status", "IEQUALS", status));
        }

        SearchConditionRequest conditionRequest;
        if (conditions.isEmpty()) {
            conditionRequest = null;
        } else {
            conditionRequest = SearchConditionRequest.group("AND", conditions.toArray(new Condition[0]));
        }

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                "Pet",
                ENTITY_VERSION,
                conditionRequest,
                true // inMemory search for optimization
        );
        ArrayNode arrayNode = itemsFuture.get();

        List<Pet> pets = new ArrayList<>();
        for (int i = 0; i < arrayNode.size(); i++) {
            ObjectNode objNode = (ObjectNode) arrayNode.get(i);
            pets.add(JsonUtils.convertValue(objNode, Pet.class));
        }

        return ResponseEntity.ok(pets);
    }

    // ------ Event-driven processing methods ------

    private void processPetIngestionJob(PetIngestionJob job) {
        logger.info("Processing PetIngestionJob with technicalId: {}", job.getTechnicalId());
        job.setStatus("PROCESSING");

        if (job.getSource() == null || job.getSource().isBlank()) {
            logger.error("Invalid source URL for PetIngestionJob technicalId {}", job.getTechnicalId());
            job.setStatus("FAILED");
            return;
        }

        // Simulate fetch data from Petstore API (mocked here)
        logger.info("Fetching pet data from source: {}", job.getSource());

        // Mock creation of two Pet entities locally and add them via entityService
        Pet pet1 = new Pet();
        pet1.setPetId("pet-001");
        pet1.setName("Fluffy");
        pet1.setCategory("Cat");
        pet1.setPhotoUrls(Collections.singletonList("http://example.com/fluffy.jpg"));
        pet1.setTags(Arrays.asList("cute", "small"));
        pet1.setStatus("AVAILABLE");

        try {
            UUID pet1Id = entityService.addItem("Pet", ENTITY_VERSION, pet1).get();
            pet1.setTechnicalId(pet1Id);
            processPet(pet1);
        } catch (Exception e) {
            logger.error("Failed to add/process pet1: {}", e.getMessage());
            job.setStatus("FAILED");
            return;
        }

        Pet pet2 = new Pet();
        pet2.setPetId("pet-002");
        pet2.setName("Buddy");
        pet2.setCategory("Dog");
        pet2.setPhotoUrls(Collections.singletonList("http://example.com/buddy.jpg"));
        pet2.setTags(Arrays.asList("friendly", "large"));
        pet2.setStatus("AVAILABLE");

        try {
            UUID pet2Id = entityService.addItem("Pet", ENTITY_VERSION, pet2).get();
            pet2.setTechnicalId(pet2Id);
            processPet(pet2);
        } catch (Exception e) {
            logger.error("Failed to add/process pet2: {}", e.getMessage());
            job.setStatus("FAILED");
            return;
        }

        job.setStatus("COMPLETED");
        logger.info("PetIngestionJob with technicalId {} completed successfully", job.getTechnicalId());
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());

        if (pet.getName() == null || pet.getName().isBlank()) {
            logger.error("Pet name is blank for Pet technicalId: {}", pet.getTechnicalId());
            return;
        }
        if (pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet category is blank for Pet technicalId: {}", pet.getTechnicalId());
            return;
        }
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            logger.error("Pet status is blank for Pet technicalId: {}", pet.getTechnicalId());
            return;
        }

        if (pet.getTags() == null) {
            pet.setTags(new ArrayList<>());
        }
        if (pet.getPhotoUrls() == null) {
            pet.setPhotoUrls(new ArrayList<>());
        }

        logger.info("Pet with technicalId {} processed successfully", pet.getTechnicalId());
    }

    // Utility class for JSON conversion (use Jackson ObjectMapper)
    private static class JsonUtils {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convertValue(Object fromValue, Class<T> toValueType) {
            return mapper.convertValue(fromValue, toValueType);
        }
    }
}