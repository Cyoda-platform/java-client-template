package com.java_template.application.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import com.java_template.common.service.EntityService;
import static com.java_template.common.config.Config.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;

// Import entities
import com.java_template.application.entity.PetIngestionJob;
import com.java_template.application.entity.Pet;

@RestController
@RequestMapping(path = "/api")
@Slf4j
public class Controller {

    private final EntityService entityService;

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    @PostMapping("/pet-ingestion-jobs")
    public CompletableFuture<ResponseEntity<Map<String, String>>> createPetIngestionJob(@RequestBody Map<String, String> request) {
        try {
            PetIngestionJob newJob = new PetIngestionJob();
            newJob.setStatus("PENDING");
            newJob.setStartTime(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            newJob.setSourceApiUrl("https://petstore.swagger.io/v2");
            newJob.setTargetPetStatus(request.getOrDefault("targetStatus", "available"));

            if (!newJob.isValid()) {
                log.error("Invalid PetIngestionJob data: {}", newJob);
                return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
            }

            return entityService.addItem(PetIngestionJob.ENTITY_NAME, ENTITY_VERSION, newJob)
                .thenApply(technicalId -> {
                    log.info("PetIngestionJob created with technicalId: {}", technicalId);
                    // As per EDA principle, saving the entity triggers the process method
                    // For prototype, we simulate the trigger here. In real Cyoda, this is automatic.
                    processPetIngestionJob(technicalId.toString(), newJob); // Convert UUID to String for prototype
                    Map<String, String> response = new HashMap<>();
                    response.put("technicalId", technicalId.toString());
                    return new ResponseEntity<>(response, HttpStatus.CREATED);
                })
                .exceptionally(ex -> {
                    log.error("Error creating PetIngestionJob: {}", ex.getMessage(), ex);
                    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        } catch (IllegalArgumentException e) {
            log.error("Bad request for PetIngestionJob creation: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
        } catch (Exception e) {
            log.error("An unexpected error occurred during PetIngestionJob creation: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    @GetMapping("/pet-ingestion-jobs/{technicalId}")
    public CompletableFuture<ResponseEntity<PetIngestionJob>> getPetIngestionJob(@PathVariable String technicalId) {
        try {
            UUID uuidTechnicalId = UUID.fromString(technicalId);
            return entityService.getItem(PetIngestionJob.ENTITY_NAME, ENTITY_VERSION, uuidTechnicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null) {
                        log.info("PetIngestionJob with technicalId {} not found.", technicalId);
                        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                    }
                    PetIngestionJob job = new PetIngestionJob();
                    job.setStatus(objectNode.get("status").asText());
                    job.setStartTime(objectNode.get("startTime").asText());
                    if (objectNode.has("endTime") && !objectNode.get("endTime").isNull()) {
                        job.setEndTime(objectNode.get("endTime").asText());
                    }
                    if (objectNode.has("ingestedPetCount") && !objectNode.get("ingestedPetCount").isNull()) {
                        job.setIngestedPetCount(objectNode.get("ingestedPetCount").asInt());
                    }
                    if (objectNode.has("errorMessage") && !objectNode.get("errorMessage").isNull()) {
                        job.setErrorMessage(objectNode.get("errorMessage").asText());
                    }
                    job.setSourceApiUrl(objectNode.get("sourceApiUrl").asText());
                    job.setTargetPetStatus(objectNode.get("targetPetStatus").asText());
                    // Assuming technicalId is not part of the entity's direct fields but handled by the service
                    return new ResponseEntity<>(job, HttpStatus.OK);
                })
                .exceptionally(ex -> {
                    log.error("Error retrieving PetIngestionJob with technicalId {}: {}", technicalId, ex.getMessage(), ex);
                    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", technicalId, e);
            return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
        } catch (Exception e) {
            log.error("An unexpected error occurred while retrieving PetIngestionJob: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    @GetMapping("/pets/available")
    public CompletableFuture<ResponseEntity<List<Pet>>> getAvailablePets() {
        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.status", "EQUALS", "available")
            );
            return entityService.getItemsByCondition(Pet.ENTITY_NAME, ENTITY_VERSION, condition, true)
                .thenApply(arrayNode -> {
                    List<Pet> pets = new ArrayList<>();
                    for (int i = 0; i < arrayNode.size(); i++) {
                        ObjectNode objectNode = (ObjectNode) arrayNode.get(i);
                        pets.add(mapObjectNodeToPet(objectNode));
                    }
                    return new ResponseEntity<>(pets, HttpStatus.OK);
                })
                .exceptionally(ex -> {
                    log.error("Error retrieving available pets: {}", ex.getMessage(), ex);
                    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        } catch (Exception e) {
            log.error("An unexpected error occurred while retrieving available pets: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    @GetMapping("/pets/{technicalId}")
    public CompletableFuture<ResponseEntity<Pet>> getPet(@PathVariable String technicalId) {
        try {
            UUID uuidTechnicalId = UUID.fromString(technicalId);
            return entityService.getItem(Pet.ENTITY_NAME, ENTITY_VERSION, uuidTechnicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null) {
                        log.info("Pet with technicalId {} not found.", technicalId);
                        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                    }
                    return new ResponseEntity<>(mapObjectNodeToPet(objectNode), HttpStatus.OK);
                })
                .exceptionally(ex -> {
                    log.error("Error retrieving Pet with technicalId {}: {}", technicalId, ex.getMessage(), ex);
                    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        } catch (IllegalArgumentException e) {
            log.error("Invalid technicalId format: {}", technicalId, e);
            return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
        } catch (Exception e) {
            log.error("An unexpected error occurred while retrieving pet: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    @GetMapping("/pets/search")
    public CompletableFuture<ResponseEntity<List<Pet>>> searchPets(@RequestParam(required = false) String name,
                                                                   @RequestParam(required = false) String category) {
        try {
            if (name == null && category == null) {
                log.error("Search criteria missing. Either 'name' or 'category' must be provided.");
                return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
            }

            SearchConditionRequest condition;
            if (name != null && category != null) {
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.name", "IEQUALS", name),
                    Condition.of("$.category", "IEQUALS", category)
                );
            } else if (name != null) {
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.name", "IEQUALS", name)
                );
            } else { // category != null
                condition = SearchConditionRequest.group("AND",
                    Condition.of("$.category", "IEQUALS", category)
                );
            }

            return entityService.getItemsByCondition(Pet.ENTITY_NAME, ENTITY_VERSION, condition, true)
                .thenApply(arrayNode -> {
                    List<Pet> pets = new ArrayList<>();
                    for (int i = 0; i < arrayNode.size(); i++) {
                        ObjectNode objectNode = (ObjectNode) arrayNode.get(i);
                        pets.add(mapObjectNodeToPet(objectNode));
                    }
                    return new ResponseEntity<>(pets, HttpStatus.OK);
                })
                .exceptionally(ex -> {
                    log.error("Error searching pets by name '{}' and category '{}': {}", name, category, ex.getMessage(), ex);
                    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                });
        } catch (Exception e) {
            log.error("An unexpected error occurred while searching pets: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));
        }
    }

    // Helper method to map ObjectNode to Pet
    private Pet mapObjectNodeToPet(ObjectNode objectNode) {
        Pet pet = new Pet();
        pet.setPetstoreId(objectNode.get("petstoreId").asLong());
        pet.setName(objectNode.get("name").asText());
        pet.setStatus(objectNode.get("status").asText());
        pet.setCategory(objectNode.get("category").asText());

        List<String> photoUrls = new ArrayList<>();
        if (objectNode.has("photoUrls") && objectNode.get("photoUrls").isArray()) {
            objectNode.get("photoUrls").forEach(node -> photoUrls.add(node.asText()));
        }
        pet.setPhotoUrls(photoUrls);

        List<String> tags = new ArrayList<>();
        if (objectNode.has("tags") && objectNode.get("tags").isArray()) {
            objectNode.get("tags").forEach(node -> tags.add(node.asText()));
        }
        pet.setTags(tags);

        if (objectNode.has("funFact") && !objectNode.get("funFact").isNull()) {
            pet.setFunFact(objectNode.get("funFact").asText());
        }
        return pet;
    }

    // CRITICAL: processEntityName() methods - fully implement business logic here
    // These methods simulate the event-driven processing triggered after entity persistence.

    private void processPetIngestionJob(String technicalId, PetIngestionJob job) {
        log.info("Starting processPetIngestionJob for job technicalId: {}", technicalId);
        try {
            // Update job status to IN_PROGRESS
            job.setStatus("IN_PROGRESS");
            // Simulate saving the updated job status
            entityService.addItem(PetIngestionJob.ENTITY_NAME, ENTITY_VERSION, job); // This would create a new version of the job

            // Simulate Data Fetching from external Petstore API
            // In a real scenario, this would be an actual HTTP call
            log.info("Fetching pet data from Petstore API for status: {}", job.getTargetPetStatus());
            List<Map<String, Object>> fetchedPetsData = new ArrayList<>();
            // Mock data for prototype
            if ("available".equals(job.getTargetPetStatus())) {
                fetchedPetsData.add(new HashMap<String, Object>() {{
                    put("id", 12345L);
                    put("name", "Buddy");
                    put("status", "available");
                    put("category", new HashMap<String, String>() {{ put("name", "Dog"); }});
                    put("photoUrls", Arrays.asList("http://example.com/buddy.jpg"));
                    put("tags", Arrays.asList(new HashMap<String, String>() {{ put("name", "friendly"); }}, new HashMap<String, String>() {{ put("name", "playful"); }}));
                }});
                fetchedPetsData.add(new HashMap<String, Object>() {{
                    put("id", 67890L);
                    put("name", "Whiskers");
                    put("status", "available");
                    put("category", new HashMap<String, String>() {{ put("name", "Cat"); }});
                    put("photoUrls", Arrays.asList("http://example.com/whiskers.jpg"));
                    put("tags", Arrays.asList(new HashMap<String, String>() {{ put("name", "cute"); }}, new HashMap<String, String>() {{ put("name", "sleepy"); }}));
                }});
            }

            int ingestedCount = 0;
            // Data Processing & Entity Creation
            for (Map<String, Object> petData : fetchedPetsData) {
                Pet pet = new Pet();
                pet.setPetstoreId((Long) petData.get("id"));
                pet.setName((String) petData.get("name"));
                pet.setStatus((String) petData.get("status"));
                if (petData.get("category") instanceof Map) {
                    pet.setCategory((String) ((Map) petData.get("category")).get("name"));
                }
                pet.setPhotoUrls((List<String>) petData.get("photoUrls"));

                List<String> tags = new ArrayList<>();
                if (petData.get("tags") instanceof List) {
                    for (Object tagObj : (List) petData.get("tags")) {
                        if (tagObj instanceof Map) {
                            tags.add((String) ((Map) tagObj).get("name"));
                        }
                    }
                }
                pet.setTags(tags);

                // Generate Fun Fact
                pet.setFunFact(generateFunFact(pet));

                if (pet.isValid()) {
                    // Save new Pet Entity - this automatically triggers processPet()
                    entityService.addItem(Pet.ENTITY_NAME, ENTITY_VERSION, pet);
                    ingestedCount++;
                } else {
                    log.warn("Skipping invalid pet data during ingestion: {}", pet);
                }
            }

            // Completion
            job.setStatus("COMPLETED");
            job.setIngestedPetCount(ingestedCount);
            job.setEndTime(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            entityService.addItem(PetIngestionJob.ENTITY_NAME, ENTITY_VERSION, job); // Save final status
            log.info("PetIngestionJob {} completed successfully. Ingested {} pets.", technicalId, ingestedCount);

        } catch (Exception e) {
            // Failure
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage());
            job.setEndTime(Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            entityService.addItem(PetIngestionJob.ENTITY_NAME, ENTITY_VERSION, job); // Save final status with error
            log.error("PetIngestionJob {} failed: {}", technicalId, e.getMessage(), e);
        }
    }

    private void processPet(String technicalId, Pet pet) {
        log.info("Processing Pet entity with technicalId: {}", technicalId);
        // This method serves as a finalization step.
        // For this application, the primary processing (like funFact generation)
        // is handled during ingestion in processPetIngestionJob.
        // This method mainly confirms readiness or could be extended for future validations.
        log.info("Pet {} is ready for retrieval.", pet.getName());
    }

    private String generateFunFact(Pet pet) {
        if ("Dog".equalsIgnoreCase(pet.getCategory())) {
            return pet.getName() + " loves chasing squirrels in the park!";
        } else if ("Cat".equalsIgnoreCase(pet.getCategory())) {
            return pet.getName() + " can sleep up to 16 hours a day!";
        } else {
            return pet.getName() + " has a unique charm that makes everyone smile!";
        }
    }
}