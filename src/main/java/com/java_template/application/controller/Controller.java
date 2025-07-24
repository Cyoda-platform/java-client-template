package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/purrfectPetsJob")
    public ResponseEntity<?> createPurrfectPetsJob(@RequestBody PurrfectPetsJob job) {
        if (job == null || !job.isValid()) {
            log.error("Invalid PurrfectPetsJob provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid PurrfectPetsJob data");
        }
        job.setStatus("PENDING");
        CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.join();
        job.setTechnicalId(technicalId);
        log.info("Created PurrfectPetsJob with technicalId: {}", technicalId);

        processPurrfectPetsJob(job);

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable UUID id) {
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("PurrfectPetsJob", ENTITY_VERSION, id);
        ObjectNode jobNode = jobFuture.join();
        if (jobNode == null || jobNode.isEmpty()) {
            log.error("PurrfectPetsJob not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PurrfectPetsJob not found");
        }
        try {
            PurrfectPetsJob job = mapper.treeToValue(jobNode, PurrfectPetsJob.class);
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            log.error("Error deserializing PurrfectPetsJob {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading PurrfectPetsJob data");
        }
    }

    @PostMapping("/pet")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        if (pet == null || !pet.isValid()) {
            log.error("Invalid Pet provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid Pet data");
        }
        pet.setStatus("AVAILABLE");
        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.join();
        pet.setTechnicalId(technicalId);
        log.info("Created Pet with technicalId: {}", technicalId);

        processPet(pet);

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable UUID id) {
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, id);
        ObjectNode petNode = petFuture.join();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Pet not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        try {
            Pet pet = mapper.treeToValue(petNode, Pet.class);
            return ResponseEntity.ok(pet);
        } catch (Exception e) {
            log.error("Error deserializing Pet {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading Pet data");
        }
    }

    @PostMapping("/adoptionRequest")
    public ResponseEntity<?> createAdoptionRequest(@RequestBody AdoptionRequest request) {
        if (request == null || !request.isValid()) {
            log.error("Invalid AdoptionRequest provided");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid AdoptionRequest data");
        }
        request.setStatus("PENDING");
        if (request.getRequestDate() == null) {
            request.setRequestDate(LocalDateTime.now());
        }
        CompletableFuture<UUID> idFuture = entityService.addItem("AdoptionRequest", ENTITY_VERSION, request);
        UUID technicalId = idFuture.join();
        request.setTechnicalId(technicalId);
        log.info("Created AdoptionRequest with technicalId: {}", technicalId);

        processAdoptionRequest(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable UUID id) {
        CompletableFuture<ObjectNode> reqFuture = entityService.getItem("AdoptionRequest", ENTITY_VERSION, id);
        ObjectNode reqNode = reqFuture.join();
        if (reqNode == null || reqNode.isEmpty()) {
            log.error("AdoptionRequest not found with technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        try {
            AdoptionRequest request = mapper.treeToValue(reqNode, AdoptionRequest.class);
            return ResponseEntity.ok(request);
        } catch (Exception e) {
            log.error("Error deserializing AdoptionRequest {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading AdoptionRequest data");
        }
    }

    private void processPurrfectPetsJob(PurrfectPetsJob job) {
        log.info("Processing PurrfectPetsJob with technicalId: {}", job.getTechnicalId());
        try {
            String action = job.getAction();
            String payload = job.getPayload();
            if (action == null || action.isBlank() || payload == null || payload.isBlank()) {
                job.setStatus("FAILED");
                log.error("Invalid action or payload in job {}", job.getTechnicalId());
                return;
            }

            if ("AddPet".equalsIgnoreCase(action)) {
                Pet pet = mapper.readValue(payload, Pet.class);
                pet.setStatus("AVAILABLE");
                CompletableFuture<UUID> petIdFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
                UUID petTechnicalId = petIdFuture.join();
                pet.setTechnicalId(petTechnicalId);
                processPet(pet);
            } else if ("AdoptPet".equalsIgnoreCase(action)) {
                AdoptionRequest adoptionRequest = mapper.readValue(payload, AdoptionRequest.class);
                adoptionRequest.setStatus("PENDING");
                if (adoptionRequest.getRequestDate() == null) {
                    adoptionRequest.setRequestDate(LocalDateTime.now());
                }
                CompletableFuture<UUID> reqIdFuture = entityService.addItem("AdoptionRequest", ENTITY_VERSION, adoptionRequest);
                UUID adoptionRequestTechnicalId = reqIdFuture.join();
                adoptionRequest.setTechnicalId(adoptionRequestTechnicalId);
                processAdoptionRequest(adoptionRequest);
            } else {
                log.warn("Unknown job action: {}", action);
                job.setStatus("FAILED");
                return;
            }
            job.setStatus("COMPLETED");
            // Update job with new version instead of update - create new entity version with updated status
            entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job).join();
            log.info("Job {} processed successfully", job.getTechnicalId());
        } catch (Exception e) {
            job.setStatus("FAILED");
            log.error("Error processing job {}: {}", job.getTechnicalId(), e.getMessage());
        }
    }

    private void processPet(Pet pet) {
        log.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        List<String> allowedBreeds = List.of("Siamese", "Persian", "Maine Coon", "Bulldog", "Beagle");
        if (!allowedBreeds.contains(pet.getBreed())) {
            log.warn("Breed {} is not in allowed list for pet {}", pet.getBreed(), pet.getTechnicalId());
            pet.setStatus("PENDING");
        } else {
            pet.setStatus("AVAILABLE");
        }
        // Create new version with updated status
        entityService.addItem("Pet", ENTITY_VERSION, pet).join();
    }

    private void processAdoptionRequest(AdoptionRequest request) {
        log.info("Processing AdoptionRequest with technicalId: {}", request.getTechnicalId());

        // Search Pet by petId (business id), we need to find pet with matching petId field
        Condition condition = Condition.of("$.petId", "EQUALS", request.getPetId());
        SearchConditionRequest searchRequest = SearchConditionRequest.group("AND", condition);
        CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, searchRequest, true);
        ArrayNode petsArray = petsFuture.join();

        if (petsArray == null || petsArray.isEmpty()) {
            log.error("Pet with petId {} not found for adoption request {}", request.getPetId(), request.getTechnicalId());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", ENTITY_VERSION, request).join();
            return;
        }

        Pet pet = null;
        try {
            ObjectNode petNode = (ObjectNode) petsArray.get(0);
            pet = mapper.treeToValue(petNode, Pet.class);
        } catch (Exception e) {
            log.error("Error deserializing Pet for adoption request {}: {}", request.getTechnicalId(), e.getMessage());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", ENTITY_VERSION, request).join();
            return;
        }

        if (!"AVAILABLE".equalsIgnoreCase(pet.getStatus())) {
            log.warn("Pet {} is not available for adoption", pet.getTechnicalId());
            request.setStatus("REJECTED");
            entityService.addItem("AdoptionRequest", ENTITY_VERSION, request).join();
            return;
        }

        request.setStatus("APPROVED");
        entityService.addItem("AdoptionRequest", ENTITY_VERSION, request).join();

        pet.setStatus("ADOPTED");
        entityService.addItem("Pet", ENTITY_VERSION, pet).join();

        log.info("AdoptionRequest {} approved and Pet {} marked as ADOPTED", request.getTechnicalId(), pet.getTechnicalId());
    }
}