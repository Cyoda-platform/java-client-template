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
import java.util.stream.Collectors;

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

        processPetJob(petJob);

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

        processPet(pet);

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

    // --------- Process Methods ---------

    private void processPetJob(PetJob petJob) throws ExecutionException, InterruptedException {
        logger.info("Processing PetJob with ID: {}", petJob.getId());

        petJob.setStatus("PROCESSING");

        String operation = petJob.getOperation() != null ? petJob.getOperation().toUpperCase(Locale.ROOT) : "";

        switch (operation) {
            case "CREATE":
                Pet newPet = JsonUtil.fromJson(petJob.getRequestPayload(), Pet.class);
                if (newPet == null || !newPet.isValid()) {
                    petJob.setStatus("FAILED");
                    logger.error("PetJob CREATE operation failed: invalid pet data in payload");
                    updatePetJobStatus(petJob);
                    return;
                }
                newPet.setId(petIdCounter.getAndIncrement());
                newPet.setTechnicalId(UUID.randomUUID());
                UUID petTechId = entityService.addItem("Pet", ENTITY_VERSION, newPet).get();
                newPet.setTechnicalId(petTechId);
                processPet(newPet);
                petJob.setStatus("COMPLETED");
                updatePetJobStatus(petJob);
                logger.info("PetJob CREATE operation completed: Pet technicalId {}", petTechId);
                break;

            case "PROCESS":
                if (petJob.getPetId() == null) {
                    petJob.setStatus("FAILED");
                    logger.error("PetJob PROCESS operation failed: petId is missing");
                    updatePetJobStatus(petJob);
                    return;
                }
                // Find Pet by business id petJob.getPetId()
                Condition cond = Condition.of("$.id", "EQUALS", petJob.getPetId());
                SearchConditionRequest conditionRequest = SearchConditionRequest.group("AND", cond);
                ArrayNode petsNode = entityService.getItemsByCondition("Pet", ENTITY_VERSION, conditionRequest, true).get();
                if (petsNode == null || petsNode.isEmpty()) {
                    petJob.setStatus("FAILED");
                    logger.error("PetJob PROCESS operation failed: Pet not found with ID {}", petJob.getPetId());
                    updatePetJobStatus(petJob);
                    return;
                }
                Pet petToProcess = JsonUtil.toPet((ObjectNode) petsNode.get(0));
                if (petToProcess.getTags() == null) {
                    petToProcess.setTags(new ArrayList<>());
                }
                if (!petToProcess.getTags().contains("processed")) {
                    petToProcess.getTags().add("processed");
                    // Instead of update, create new Pet version (EDA principle)
                    petToProcess.setTechnicalId(UUID.randomUUID());
                    entityService.addItem("Pet", ENTITY_VERSION, petToProcess).get();
                }
                petJob.setStatus("COMPLETED");
                updatePetJobStatus(petJob);
                logger.info("PetJob PROCESS operation completed for Pet ID {}", petToProcess.getId());
                break;

            case "SEARCH":
                Map<String, String> criteria = JsonUtil.fromJson(petJob.getRequestPayload(), Map.class);
                if (criteria == null || criteria.isEmpty()) {
                    petJob.setStatus("FAILED");
                    logger.error("PetJob SEARCH operation failed: empty or invalid criteria");
                    updatePetJobStatus(petJob);
                    return;
                }

                List<Condition> condList = new ArrayList<>();
                for (Map.Entry<String, String> entry : criteria.entrySet()) {
                    String key = entry.getKey().toLowerCase(Locale.ROOT);
                    String value = entry.getValue();
                    switch (key) {
                        case "category":
                            condList.add(Condition.of("$.category", "IEQUALS", value));
                            break;
                        case "status":
                            condList.add(Condition.of("$.status", "IEQUALS", value));
                            break;
                        case "name":
                            condList.add(Condition.of("$.name", "ICONTAINS", value));
                            break;
                        default:
                            // ignore unknown criteria
                    }
                }
                SearchConditionRequest searchCond = SearchConditionRequest.group("AND", condList.toArray(new Condition[0]));
                ArrayNode matchedPetsNode = entityService.getItemsByCondition("Pet", ENTITY_VERSION, searchCond, true).get();

                logger.info("PetJob SEARCH operation found {} pets matching criteria", matchedPetsNode.size());
                petJob.setStatus("COMPLETED");
                updatePetJobStatus(petJob);
                break;

            default:
                petJob.setStatus("FAILED");
                logger.error("PetJob operation '{}' is not supported", operation);
                updatePetJobStatus(petJob);
        }
    }

    private void updatePetJobStatus(PetJob petJob) throws ExecutionException, InterruptedException {
        // Create a new version of PetJob with updated status per EDA principle
        petJob.setTechnicalId(UUID.randomUUID());
        entityService.addItem("PetJob", ENTITY_VERSION, petJob).get();
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with ID: {}", pet.getId());
        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("available");
        }
        logger.info("Pet processing completed with status: {}", pet.getStatus());
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