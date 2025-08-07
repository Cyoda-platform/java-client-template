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

            processWorkflow(technicalId, workflow);

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

            processPet(technicalId, pet);

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

            processAdoptionRequest(technicalId, adoptionRequest);

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

    // Process Methods - Business Logic Implementation

    private void processWorkflow(String technicalId, Workflow workflow) {
        logger.info("Processing Workflow with id: {}", technicalId);

        try {
            // Validation: Check pet exists and status is "available"
            // We do a getItemsByCondition to find pet by technicalId equal to workflow.getPetId()

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", workflow.getPetId())
            );

            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    condition,
                    true
            );

            ArrayNode petNodes = petsFuture.get();
            if (petNodes == null || petNodes.isEmpty()) {
                logger.error("Pet referenced by Workflow {} not found", technicalId);
                workflow.setStatus("FAILED");
                return;
            }

            ObjectNode petNode = (ObjectNode) petNodes.get(0);
            Pet pet = Pet.fromJsonNode(petNode);

            if (!"available".equalsIgnoreCase(pet.getStatus())) {
                logger.error("Pet {} is not available for Workflow {}", workflow.getPetId(), technicalId);
                workflow.setStatus("FAILED");
                return;
            }

            // Simulate triggering adoption request processing or other logic
            workflow.setStatus("COMPLETED");
            logger.info("Workflow {} processing COMPLETED", technicalId);

            // TODO: Consider saving updated workflow status if needed. Currently no update method available.

        } catch (Exception e) {
            logger.error("Exception in processWorkflow", e);
            workflow.setStatus("FAILED");
        }
    }

    private void processPet(String technicalId, Pet pet) {
        logger.info("Processing Pet with id: {}", technicalId);

        try {
            // Validation already done, simulate enrichment or sync with Petstore API

            // Example: Log sync action (real external API call would be here)
            logger.info("Syncing Pet {} data with external Petstore API", technicalId);

            // Mark processing as complete
            logger.info("Pet {} processing COMPLETED", technicalId);

        } catch (Exception e) {
            logger.error("Exception in processPet", e);
        }
    }

    private void processAdoptionRequest(String technicalId, AdoptionRequest adoptionRequest) {
        logger.info("Processing AdoptionRequest with id: {}", technicalId);

        try {
            // Validation: Check if pet exists and is available
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.technicalId", "EQUALS", adoptionRequest.getPetId())
            );

            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    condition,
                    true
            );

            ArrayNode petNodes = petsFuture.get();
            if (petNodes == null || petNodes.isEmpty()) {
                logger.error("Pet {} referenced in AdoptionRequest {} not found", adoptionRequest.getPetId(), technicalId);
                adoptionRequest.setStatus("REJECTED");
                return;
            }

            ObjectNode petNode = (ObjectNode) petNodes.get(0);
            Pet pet = Pet.fromJsonNode(petNode);

            if (!"available".equalsIgnoreCase(pet.getStatus())) {
                logger.error("Pet {} is not available for AdoptionRequest {}", adoptionRequest.getPetId(), technicalId);
                adoptionRequest.setStatus("REJECTED");
                return;
            }

            // Simulate updating pet status by creating a new Pet entity version (immutable)
            Pet newPetVersion = new Pet();
            newPetVersion.setName(pet.getName());
            newPetVersion.setCategory(pet.getCategory());
            newPetVersion.setPhotoUrls(pet.getPhotoUrls());
            newPetVersion.setTags(pet.getTags());
            newPetVersion.setCreatedAt(pet.getCreatedAt());
            newPetVersion.setStatus("pending");

            CompletableFuture<UUID> newPetIdFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    newPetVersion
            );
            UUID newPetTechnicalId = newPetIdFuture.get();
            logger.info("Created new Pet version {} with status 'pending' due to AdoptionRequest {}", newPetTechnicalId, technicalId);

            adoptionRequest.setStatus("APPROVED");
            logger.info("AdoptionRequest {} APPROVED", technicalId);

            // TODO: Consider saving updated adoptionRequest status if needed. Currently no update method available.

        } catch (Exception e) {
            logger.error("Exception in processAdoptionRequest", e);
            adoptionRequest.setStatus("REJECTED");
        }
    }
}