package com.java_template.prototype;

import static com.java_template.common.config.Config.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetUpdateJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // Local cache and ID counter only for minor/utility entities (none here per instructions)

    // --- PetUpdateJob Endpoints ---

    @PostMapping("/petUpdateJob")
    public ResponseEntity<?> createPetUpdateJob(@Valid @RequestBody PetUpdateJob jobRequest) throws JsonProcessingException {
        if (jobRequest == null || jobRequest.getSource() == null || jobRequest.getSource().isBlank()) {
            log.error("Invalid PetUpdateJob creation request: missing source");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Source is required");
        }

        jobRequest.setJobId(null); // clear any existing IDs to avoid confusion
        jobRequest.setId(null);
        jobRequest.setStatus("PENDING");
        jobRequest.setRequestedAt(LocalDateTime.now());

        CompletableFuture<UUID> idFuture = entityService.addItem("PetUpdateJob", ENTITY_VERSION, jobRequest);

        UUID technicalId;
        try {
            technicalId = idFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create PetUpdateJob: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        // Assign generated technicalId as string ID to jobRequest for tracking
        String generatedId = technicalId.toString();
        jobRequest.setId(generatedId);
        jobRequest.setJobId(generatedId);

        log.info("Created PetUpdateJob with technicalId: {}", generatedId);

        Map<String, String> response = new HashMap<>();
        response.put("jobId", generatedId);
        response.put("status", jobRequest.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/petUpdateJob/{id}")
    public ResponseEntity<?> getPetUpdateJob(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        // Build condition to search by technicalId (UUID)
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", UUID.fromString(id)));

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("PetUpdateJob", ENTITY_VERSION, condition);

        ArrayNode items = itemsFuture.get();
        if (items == null || items.size() == 0) {
            log.error("PetUpdateJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PetUpdateJob not found");
        }
        ObjectNode jobNode = (ObjectNode) items.get(0);
        PetUpdateJob job = objectMapper.treeToValue(jobNode, PetUpdateJob.class);
        return ResponseEntity.ok(job);
    }

    // --- Pet Endpoints ---

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet petRequest) throws JsonProcessingException {
        if (petRequest == null
                || petRequest.getName() == null || petRequest.getName().isBlank()
                || petRequest.getCategory() == null || petRequest.getCategory().isBlank()
                || petRequest.getStatus() == null || petRequest.getStatus().isBlank()) {
            log.error("Invalid Pet creation request: missing required fields");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Name, category, and status are required");
        }

        petRequest.setId(null);
        petRequest.setPetId(null);
        petRequest.setTechnicalId(null);

        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, petRequest);

        UUID technicalId;
        try {
            technicalId = idFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create Pet: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        String generatedId = technicalId.toString();
        petRequest.setId(generatedId);
        petRequest.setPetId(generatedId);
        petRequest.setTechnicalId(technicalId);

        log.info("Created Pet with technicalId: {}", generatedId);

        Map<String, String> response = new HashMap<>();
        response.put("petId", generatedId);
        response.put("status", petRequest.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.technicalId", "EQUALS", UUID.fromString(id)));

        CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition);

        ArrayNode items = itemsFuture.get();
        if (items == null || items.size() == 0) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        ObjectNode petNode = (ObjectNode) items.get(0);
        Pet pet = objectMapper.treeToValue(petNode, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @GetMapping("/pet")
    public ResponseEntity<?> getAllPets() throws ExecutionException, InterruptedException {
        CompletableFuture<ArrayNode> itemsFuture = entityService.getItems("Pet", ENTITY_VERSION);
        ArrayNode pets = itemsFuture.get();
        return ResponseEntity.ok(pets);
    }

    // --- Other methods remain intact, process methods removed ---
}