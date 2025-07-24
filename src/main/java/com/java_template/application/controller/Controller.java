package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // Local cache and counter for Pet entity ID assignment only (business ID)
    private final AtomicLong petIdCounter = new AtomicLong(1);

    // --------- PetJob Endpoints ---------

    @PostMapping("/pet-job")
    public ResponseEntity<?> createPetJob(@RequestBody PetJob petJob) throws ExecutionException, InterruptedException {
        if (petJob == null) {
            logger.error("PetJob creation failed: request body is null");
            return ResponseEntity.badRequest().body("PetJob data is required");
        }

        petJob.setId("PJ-" + UUID.randomUUID()); // business ID as unique string; original used counter but now UUID for uniqueness
        petJob.setStatus("PENDING");
        if (petJob.getTechnicalId() == null) {
            petJob.setTechnicalId(UUID.randomUUID());
        }
        if (!petJob.isValid()) {
            logger.error("PetJob creation failed: invalid data");
            return ResponseEntity.badRequest().body("Invalid PetJob data");
        }

        // Add PetJob via entityService
        UUID technicalId = entityService.addItem("PetJob", ENTITY_VERSION, petJob).get();
        petJob.setTechnicalId(technicalId);

        logger.info("PetJob created with technicalId: {}", technicalId);

        Map<String, String> resp = new HashMap<>();
        resp.put("jobId", petJob.getId());
        resp.put("status", petJob.getStatus());
        return ResponseEntity.status(201).body(resp);
    }

    @GetMapping("/pet-job/{id}")
    public ResponseEntity<?> getPetJob(@PathVariable String id) throws ExecutionException, InterruptedException {
        // Search PetJob by business ID ("id" field) using getItemsByCondition
        Condition cond = Condition.of("$.id", "EQUALS", id);
        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond);
        CompletableFuture<ArrayNode> future = entityService.getItemsByCondition("PetJob", ENTITY_VERSION, conditionRequest, true);
        ArrayNode results = future.get();

        if (results == null || results.isEmpty()) {
            logger.error("PetJob not found: ID {}", id);
            return ResponseEntity.status(404).body("PetJob not found");
        }
        // Extract the first matched PetJob as ObjectNode and convert to PetJob
        ObjectNode node = (ObjectNode) results.get(0);
        PetJob petJob = JsonUtil.toPetJob(node);

        return ResponseEntity.ok(petJob);
    }

    // --------- Pet Endpoints ---------

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (pet == null) {
            logger.error("Pet creation failed: request body is null");
            return ResponseEntity.badRequest().body("Pet data is required");
        }

        pet.setId(petIdCounter.getAndIncrement());
        if (pet.getTechnicalId() == null) {
            pet.setTechnicalId(UUID.randomUUID());
        }
        if (!pet.isValid()) {
            logger.error("Pet creation failed: invalid data");
            return ResponseEntity.badRequest().body("Invalid Pet data");
        }

        // Add Pet via entityService
        UUID technicalId = entityService.addItem("Pet", ENTITY_VERSION, pet).get();
        pet.setTechnicalId(technicalId);

        logger.info("Pet created with technicalId: {}", technicalId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", pet.getId());
        resp.put("status", pet.getStatus());
        return ResponseEntity.status(201).body(resp);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable Long id) throws ExecutionException, InterruptedException {
        // Search Pet by business ID ("id" field) using getItemsByCondition
        Condition cond = Condition.of("$.id", "EQUALS", id);
        SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond);
        CompletableFuture<ArrayNode> future = entityService.getItemsByCondition("Pet", ENTITY_VERSION, conditionRequest, true);
        ArrayNode results = future.get();

        if (results == null || results.isEmpty()) {
            logger.error("Pet not found: ID {}", id);
            return ResponseEntity.status(404).body("Pet not found");
        }
        ObjectNode node = (ObjectNode) results.get(0);
        Pet pet = JsonUtil.toPet(node);

        return ResponseEntity.ok(pet);
    }

    // Utility JSON conversions (to/from ObjectNode)
    private static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        public static <T> T fromJson(String json, Class<T> clazz) {
            if (json == null) return null;
            try {
                return mapper.readValue(json, clazz);
            } catch (Exception e) {
                logger.error("JSON deserialization error: {}", e.getMessage());
                return null;
            }
        }

        public static <T> T fromJson(String json, java.lang.reflect.Type typeOfT) {
            if (json == null) return null;
            try {
                com.fasterxml.jackson.core.type.TypeReference<T> typeRef = new com.fasterxml.jackson.core.type.TypeReference<>() {
                    @Override
                    public java.lang.reflect.Type getType() {
                        return typeOfT;
                    }
                };
                return mapper.readValue(json, typeRef);
            } catch (Exception e) {
                logger.error("JSON deserialization error: {}", e.getMessage());
                return null;
            }
        }

        public static PetJob toPetJob(ObjectNode node) {
            try {
                return mapper.treeToValue(node, PetJob.class);
            } catch (Exception e) {
                logger.error("Error converting ObjectNode to PetJob: {}", e.getMessage());
                return null;
            }
        }

        public static Pet toPet(ObjectNode node) {
            try {
                return mapper.treeToValue(node, Pet.class);
            } catch (Exception e) {
                logger.error("Error converting ObjectNode to Pet: {}", e.getMessage());
                return null;
            }
        }
    }
}