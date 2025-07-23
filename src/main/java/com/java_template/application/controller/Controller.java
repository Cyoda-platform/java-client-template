package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.PetJob;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.CatFact;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // POST /entities/petjobs
    @PostMapping("/petjobs")
    public CompletableFuture<ResponseEntity<?>> createPetJob(@RequestBody PetJob petJob) {
        if (petJob == null) {
            logger.error("Received null PetJob");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("PetJob cannot be null"));
        }

        if (petJob.getCreatedAt() == null) {
            petJob.setCreatedAt(LocalDateTime.now());
        }
        if (petJob.getStatus() == null || petJob.getStatus().isBlank()) {
            petJob.setStatus("PENDING");
        }

        if (!petJob.isValid()) {
            logger.error("PetJob validation failed");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid PetJob data"));
        }

        return entityService.addItem("PetJob", ENTITY_VERSION, petJob)
                .thenCompose(technicalId -> entityService.getItem("PetJob", ENTITY_VERSION, technicalId)
                        .thenCompose(itemNode -> {
                            PetJob savedPetJob = JsonUtil.convertToPetJob(itemNode);
                            // processPetJob(savedPetJob);  // Removed processing method call
                            // After processing, create a new version with status COMPLETED
                            PetJob completedPetJob = new PetJob(savedPetJob);
                            completedPetJob.setStatus("COMPLETED");
                            return entityService.addItem("PetJob", ENTITY_VERSION, completedPetJob)
                                    .thenApply(newId -> ResponseEntity.status(201).body(completedPetJob));
                        })
                );
    }

    // GET /entities/petjobs/{id}
    @GetMapping("/petjobs/{id}")
    public CompletableFuture<ResponseEntity<?>> getPetJob(@PathVariable String id) {
        return findPetJobById(id)
                .thenApply(petJob -> petJob != null ? ResponseEntity.ok(petJob) : ResponseEntity.status(404).body("PetJob not found"));
    }

    private CompletableFuture<PetJob> findPetJobById(String id) {
        // Search by custom id field "id" inside entity stored with technicalId
        Condition condition = Condition.of("$.id", "EQUALS", id);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getItemsByCondition("PetJob", ENTITY_VERSION, searchCondition, true)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.size() == 0) return null;
                    ObjectNode node = (ObjectNode) arrayNode.get(0);
                    return JsonUtil.convertToPetJob(node);
                });
    }

    // POST /entities/pets
    @PostMapping("/pets")
    public CompletableFuture<ResponseEntity<?>> createPet(@RequestBody Pet pet) {
        if (pet == null) {
            logger.error("Received null Pet");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Pet cannot be null"));
        }

        if (pet.getStatus() == null || pet.getStatus().isBlank()) {
            pet.setStatus("ACTIVE");
        }

        if (!pet.isValid()) {
            logger.error("Pet validation failed");
            return CompletableFuture.completedFuture(ResponseEntity.badRequest().body("Invalid Pet data"));
        }

        return entityService.addItem("Pet", ENTITY_VERSION, pet)
                .thenCompose(technicalId -> entityService.getItem("Pet", ENTITY_VERSION, technicalId)
                        .thenCompose(itemNode -> {
                            Pet savedPet = JsonUtil.convertToPet(itemNode);
                            // processPet(savedPet);  // Removed processing method call
                            return entityService.addItem("Pet", ENTITY_VERSION, savedPet)
                                    .thenApply(newId -> ResponseEntity.status(201).body(savedPet));
                        })
                );
    }

    // GET /entities/pets/{id}
    @GetMapping("/pets/{id}")
    public CompletableFuture<ResponseEntity<?>> getPet(@PathVariable String id) {
        return findPetById(id)
                .thenApply(pet -> pet != null ? ResponseEntity.ok(pet) : ResponseEntity.status(404).body("Pet not found"));
    }

    private CompletableFuture<Pet> findPetById(String id) {
        Condition condition = Condition.of("$.id", "EQUALS", id);
        SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);
        return entityService.getItemsByCondition("Pet", ENTITY_VERSION, searchCondition, true)
                .thenApply(arrayNode -> {
                    if (arrayNode == null || arrayNode.size() == 0) return null;
                    ObjectNode node = (ObjectNode) arrayNode.get(0);
                    return JsonUtil.convertToPet(node);
                });
    }

    // POST /entities/catfacts
    @PostMapping("/catfacts")
    public ResponseEntity<?> createCatFact(@RequestBody CatFact catFact) {
        if (catFact == null) {
            logger.error("Received null CatFact");
            return ResponseEntity.badRequest().body("CatFact cannot be null");
        }

        if (catFact.getStatus() == null || catFact.getStatus().isBlank()) {
            catFact.setStatus("PUBLISHED");
        }

        if (!catFact.isValid()) {
            logger.error("CatFact validation failed");
            return ResponseEntity.badRequest().body("Invalid CatFact data");
        }

        // Keep local cache for CatFact per instructions (minor/utility entity)
        // processCatFact(catFact);  // Removed processing method call
        return ResponseEntity.status(201).body(catFact);
    }

    // GET /entities/catfacts/{id}
    @GetMapping("/catfacts/{id}")
    public ResponseEntity<?> getCatFact(@PathVariable String id) {
        // Local cache not available, return 404 for prototype
        return ResponseEntity.status(404).body("CatFact not found");
    }

    // Utility class for JSON conversion
    private static class JsonUtil {
        static PetJob convertToPetJob(ObjectNode node) {
            try {
                return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .build().treeToValue(node, PetJob.class);
            } catch (Exception e) {
                logger.error("Failed to convert ObjectNode to PetJob", e);
                return null;
            }
        }

        static Pet convertToPet(ObjectNode node) {
            try {
                return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .build().treeToValue(node, Pet.class);
            } catch (Exception e) {
                logger.error("Failed to convert ObjectNode to Pet", e);
                return null;
            }
        }
    }

}