package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping("/controller")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper mapper;

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

        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    @GetMapping("/purrfectPetsJob/{id}")
    public ResponseEntity<?> getPurrfectPetsJob(@PathVariable String id) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> jobFuture = entityService.getItem("PurrfectPetsJob", ENTITY_VERSION, technicalId);
        ObjectNode jobNode = jobFuture.join();
        if (jobNode == null || jobNode.isEmpty()) {
            log.error("PurrfectPetsJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("PurrfectPetsJob not found");
        }
        PurrfectPetsJob job = mapper.treeToValue(jobNode, PurrfectPetsJob.class);
        return ResponseEntity.ok(job);
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

        return ResponseEntity.status(HttpStatus.CREATED).body(pet);
    }

    @GetMapping("/pet/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> petFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
        ObjectNode petNode = petFuture.join();
        if (petNode == null || petNode.isEmpty()) {
            log.error("Pet not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pet not found");
        }
        Pet pet = mapper.treeToValue(petNode, Pet.class);
        return ResponseEntity.ok(pet);
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

        return ResponseEntity.status(HttpStatus.CREATED).body(request);
    }

    @GetMapping("/adoptionRequest/{id}")
    public ResponseEntity<?> getAdoptionRequest(@PathVariable String id) throws JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> reqFuture = entityService.getItem("AdoptionRequest", ENTITY_VERSION, technicalId);
        ObjectNode reqNode = reqFuture.join();
        if (reqNode == null || reqNode.isEmpty()) {
            log.error("AdoptionRequest not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("AdoptionRequest not found");
        }
        AdoptionRequest request = mapper.treeToValue(reqNode, AdoptionRequest.class);
        return ResponseEntity.ok(request);
    }
}