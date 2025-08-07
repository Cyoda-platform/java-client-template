package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/api")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // WORKFLOW Endpoints

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@Valid @RequestBody Workflow workflow) throws ExecutionException, InterruptedException {
        if (!workflow.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        CompletableFuture<UUID> idFuture = entityService.addItem(
                Workflow.ENTITY_NAME,
                ENTITY_VERSION,
                workflow
        );
        UUID technicalIdUUID = idFuture.get();
        String technicalId = technicalIdUUID.toString();

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflowById(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Workflow.ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Workflow workflow = objectMapper.treeToValue(node, Workflow.class);
        return ResponseEntity.ok(workflow);
    }

    // PET Endpoints

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@Valid @RequestBody Pet pet) throws ExecutionException, InterruptedException {
        if (!pet.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        CompletableFuture<UUID> idFuture = entityService.addItem(
                Pet.ENTITY_NAME,
                ENTITY_VERSION,
                pet
        );
        UUID technicalIdUUID = idFuture.get();
        String technicalId = technicalIdUUID.toString();

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                Pet.ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    // ADOPTION REQUEST Endpoints

    @PostMapping("/adoption-requests")
    public ResponseEntity<Map<String, String>> createAdoptionRequest(@Valid @RequestBody AdoptionRequest adoptionRequest) throws ExecutionException, InterruptedException {
        if (!adoptionRequest.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        CompletableFuture<UUID> idFuture = entityService.addItem(
                AdoptionRequest.ENTITY_NAME,
                ENTITY_VERSION,
                adoptionRequest
        );
        UUID technicalIdUUID = idFuture.get();
        String technicalId = technicalIdUUID.toString();

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/adoption-requests/{id}")
    public ResponseEntity<AdoptionRequest> getAdoptionRequestById(@PathVariable("id") String id) throws ExecutionException, InterruptedException, JsonProcessingException {
        UUID technicalId = UUID.fromString(id);
        CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                AdoptionRequest.ENTITY_NAME,
                ENTITY_VERSION,
                technicalId
        );
        ObjectNode node = itemFuture.get();
        if (node == null || node.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        AdoptionRequest adoptionRequest = objectMapper.treeToValue(node, AdoptionRequest.class);
        return ResponseEntity.ok(adoptionRequest);
    }

}