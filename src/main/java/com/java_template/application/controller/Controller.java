package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Workflow;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entities")
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /entities/workflows - create Workflow entity
    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (workflow == null || !workflow.isValid()) {
                logger.error("Invalid Workflow entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            workflow.setStatus("PENDING");
            workflow.setCreatedAt(Instant.now().toString());

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    workflow
            );
            UUID technicalId = idFuture.get();

            logger.info("Workflow created with technicalId {}", technicalId);

            // Trigger processing synchronously here
            try {
                processWorkflow(technicalId, workflow);
            } catch (Exception e) {
                logger.error("Error processing Workflow {}", technicalId, e);
                workflow.setStatus("FAILED");
                // TODO: update workflow status via entityService (update not supported, skip)
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in createWorkflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in createWorkflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /entities/workflows/{id} - get Workflow by technicalId
    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Workflow not found with technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Workflow workflow = node.traverse().readValueAs(Workflow.class);
            return ResponseEntity.ok(workflow);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for Workflow id: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("ExecutionException in getWorkflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Exception in getWorkflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /entities/pets/{id} - get Pet by technicalId
    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Pet not found with technicalId {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Pet pet = node.traverse().readValueAs(Pet.class);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for Pet id: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            logger.error("ExecutionException in getPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Exception in getPet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /entities/pets - optional get pets by category query param
    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> getPetsByCategory(@RequestParam(required = false) String category) {
        try {
            if (category == null || category.isBlank()) {
                // Get all pets
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Pet.ENTITY_NAME,
                        ENTITY_VERSION
                );
                ArrayNode nodes = itemsFuture.get();
                List<Pet> pets = new ArrayList<>();
                for (int i = 0; i < nodes.size(); i++) {
                    Pet pet = nodes.get(i).traverse().readValueAs(Pet.class);
                    pets.add(pet);
                }
                return ResponseEntity.ok(pets);
            } else {
                // Filter by category using condition
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.category", "IEQUALS", category)
                );
                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                        Pet.ENTITY_NAME,
                        ENTITY_VERSION,
                        condition,
                        true
                );
                ArrayNode nodes = filteredItemsFuture.get();
                List<Pet> pets = new ArrayList<>();
                for (int i = 0; i < nodes.size(); i++) {
                    Pet pet = nodes.get(i).traverse().readValueAs(Pet.class);
                    pets.add(pet);
                }
                return ResponseEntity.ok(pets);
            }
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException in getPetsByCategory", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Exception in getPetsByCategory", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Process Workflow: fetch pets from Petstore API by petCategory, save Pet entities immutably
    private void processWorkflow(UUID technicalId, Workflow workflow) {
        logger.info("Processing Workflow {} with petCategory {}", technicalId, workflow.getPetCategory());

        // Validate required fields again before processing
        if (workflow.getPetCategory() == null || workflow.getPetCategory().isBlank()) {
            logger.error("Workflow petCategory is missing or blank");
            workflow.setStatus("FAILED");
            // TODO: update workflow status via entityService (update not supported, skip)
            return;
        }

        // Simulate calling external Petstore API to fetch pets by category
        List<Pet> fetchedPets = fetchPetsFromPetstoreApi(workflow.getPetCategory());
        if (fetchedPets == null) {
            logger.error("Failed to fetch pets from Petstore API");
            workflow.setStatus("FAILED");
            // TODO: update workflow status via entityService (update not supported, skip)
            return;
        }

        // Save each pet immutably using entityService.addItem
        for (Pet pet : fetchedPets) {
            pet.setCreatedAt(Instant.now().toString());
            try {
                CompletableFuture<UUID> petIdFuture = entityService.addItem(
                        Pet.ENTITY_NAME,
                        ENTITY_VERSION,
                        pet
                );
                UUID petTechnicalId = petIdFuture.get();
                processPet(petTechnicalId, pet);
            } catch (Exception e) {
                logger.error("Error processing Pet", e);
            }
        }

        workflow.setStatus("COMPLETED");
        // TODO: update workflow status via entityService (update not supported, skip)
        logger.info("Workflow {} completed successfully", technicalId);
    }

    // Process Pet: enrich or tag pet data, mark as processed
    private void processPet(UUID technicalId, Pet pet) {
        logger.info("Processing Pet {} named {}", technicalId, pet.getName());
        // Example enrichment: add emoji tags based on category
        String tags = pet.getTags() != null ? pet.getTags() : "";
        if ("cats".equalsIgnoreCase(pet.getCategory())) {
            tags += (tags.isEmpty() ? "" : ",") + "😺";
        } else if ("dogs".equalsIgnoreCase(pet.getCategory())) {
            tags += (tags.isEmpty() ? "" : ",") + "🐶";
        }
        pet.setTags(tags);
        // TODO: update pet with new tags via entityService (update not supported, skip)
        logger.info("Pet {} processed with tags: {}", technicalId, pet.getTags());
    }

    // Simulated external API call to Petstore API - returns list of pets for given category
    private List<Pet> fetchPetsFromPetstoreApi(String category) {
        // In a real app, this would be an HTTP call to Petstore API.
        // Here, we simulate with dummy data.

        List<Pet> pets = new ArrayList<>();
        if ("cats".equalsIgnoreCase(category)) {
            Pet p1 = new Pet();
            p1.setPetId(101L);
            p1.setName("Whiskers");
            p1.setCategory("cats");
            p1.setStatus("available");
            p1.setPhotoUrls("http://example.com/photo1.jpg,http://example.com/photo2.jpg");
            p1.setTags("playful,fluffy");
            pets.add(p1);

            Pet p2 = new Pet();
            p2.setPetId(102L);
            p2.setName("Snowball");
            p2.setCategory("cats");
            p2.setStatus("pending");
            p2.setPhotoUrls("http://example.com/photo3.jpg");
            p2.setTags("sleepy");
            pets.add(p2);
        } else if ("dogs".equalsIgnoreCase(category)) {
            Pet p1 = new Pet();
            p1.setPetId(201L);
            p1.setName("Buddy");
            p1.setCategory("dogs");
            p1.setStatus("available");
            p1.setPhotoUrls("http://example.com/dog1.jpg");
            p1.setTags("friendly,energetic");
            pets.add(p1);
        } else {
            // Return empty list for other categories
        }
        return pets;
    }
}