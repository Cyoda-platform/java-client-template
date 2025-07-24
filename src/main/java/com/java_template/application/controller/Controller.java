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
        // processPetIngestionJob(job);  // Removed as per extraction

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
        // processPet(pet);  // Removed as per extraction

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

    // Utility class for JSON conversion (use Jackson ObjectMapper)
    private static class JsonUtils {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T convertValue(Object fromValue, Class<T> toValueType) {
            return mapper.convertValue(fromValue, toValueType);
        }
    }
}