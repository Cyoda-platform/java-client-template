package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.AdoptionRequest;
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
import java.util.stream.Collectors;
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/api")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // WORKFLOW Endpoints

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (!workflow.isValid()) {
                logger.error("Invalid Workflow entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    workflow
            );
            UUID technicalIdUUID = idFuture.get();
            String technicalId = technicalIdUUID.toString();

            logger.info("Workflow created with id: {}", technicalId);

            // processWorkflow removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createWorkflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error creating workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflowById(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Workflow not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Workflow workflow = Workflow.fromJsonNode(node);
            logger.info("Workflow retrieved with id: {}", id);
            return ResponseEntity.ok(workflow);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid id format in getWorkflowById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // PET Endpoints

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        try {
            if (!pet.isValid()) {
                logger.error("Invalid Pet entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    pet
            );
            UUID technicalIdUUID = idFuture.get();
            String technicalId = technicalIdUUID.toString();

            logger.info("Pet created with id: {}", technicalId);

            // processPet removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createPet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error creating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPetById(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("Pet not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Pet pet = Pet.fromJsonNode(node);
            logger.info("Pet retrieved with id: {}", id);
            return ResponseEntity.ok(pet);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid id format in getPetById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ADOPTION REQUEST Endpoints

    @PostMapping("/adoption-requests")
    public ResponseEntity<Map<String, String>> createAdoptionRequest(@RequestBody AdoptionRequest adoptionRequest) {
        try {
            if (!adoptionRequest.isValid()) {
                logger.error("Invalid AdoptionRequest entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    AdoptionRequest.ENTITY_NAME,
                    ENTITY_VERSION,
                    adoptionRequest
            );
            UUID technicalIdUUID = idFuture.get();
            String technicalId = technicalIdUUID.toString();

            logger.info("AdoptionRequest created with id: {}", technicalId);

            // processAdoptionRequest removed

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in createAdoptionRequest: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error creating adoption request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/adoption-requests/{id}")
    public ResponseEntity<AdoptionRequest> getAdoptionRequestById(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    AdoptionRequest.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get();
            if (node == null || node.isEmpty()) {
                logger.error("AdoptionRequest not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            AdoptionRequest adoptionRequest = AdoptionRequest.fromJsonNode(node);
            logger.info("AdoptionRequest retrieved with id: {}", id);
            return ResponseEntity.ok(adoptionRequest);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid id format in getAdoptionRequestById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            logger.error("Error retrieving adoption request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Removed all processEntity methods and their helper methods

}