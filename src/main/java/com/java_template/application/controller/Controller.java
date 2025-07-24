package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.PetOrder;
import com.java_template.application.entity.Pet;
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

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/main")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // --- Workflow endpoints ---

    @PostMapping("/workflows")
    public ResponseEntity<?> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (workflow == null || !workflow.isValid()) {
                logger.error("Invalid Workflow creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid workflow data"));
            }
            // Add workflow via entityService
            CompletableFuture<UUID> idFuture = entityService.addItem("Workflow", ENTITY_VERSION, workflow);
            UUID technicalId = idFuture.get();

            logger.info("Created Workflow with technicalId: {}", technicalId);
            processWorkflow(workflow);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error creating workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error creating workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Workflow", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Workflow not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID for workflow id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid workflow ID"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving workflow with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error retrieving workflow with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- PetOrder endpoints ---

    @PostMapping("/petOrders")
    public ResponseEntity<?> createPetOrder(@RequestBody PetOrder petOrder) {
        try {
            if (petOrder == null || !petOrder.isValid()) {
                logger.error("Invalid PetOrder creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet order data"));
            }

            // Check pet availability via entityService (simulate by querying pet with petOrder.petId and status AVAILABLE)
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", petOrder.getPetId()),
                    Condition.of("$.status", "IEQUALS", "AVAILABLE"));
            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
            ArrayNode pets = petsFuture.get();

            if (pets == null || pets.size() == 0) {
                logger.error("Pet with ID {} is not available for order", petOrder.getPetId());
                petOrder.setStatus("CANCELLED");
                // Skipping entityService update, just return CANCELLED state response
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet not available for order"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem("PetOrder", ENTITY_VERSION, petOrder);
            UUID technicalId = idFuture.get();

            logger.info("Created PetOrder with technicalId: {}", technicalId);

            processPetOrder(petOrder);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error creating pet order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error creating pet order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/petOrders/{id}")
    public ResponseEntity<?> getPetOrder(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetOrder", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("PetOrder not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PetOrder not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID for pet order id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet order ID"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving pet order with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error retrieving pet order with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Pet endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        try {
            if (pet == null || !pet.isValid()) {
                logger.error("Invalid Pet creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet data"));
            }
            CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
            UUID technicalId = idFuture.get();
            logger.info("Created Pet with technicalId: {}", technicalId);

            processPet(pet);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Illegal argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error creating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error creating pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Pet not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID for pet id: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet ID"));
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error retrieving pet with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        } catch (Exception e) {
            logger.error("Unexpected error retrieving pet with id: {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Process methods with business logic ---

    private void processWorkflow(Workflow workflow) {
        logger.info("Processing Workflow with name: {}", workflow.getWorkflowName());
        // Validate and start orchestration
        if ("CREATED".equalsIgnoreCase(workflow.getStatus())) {
            logger.info("Starting orchestration for Workflow: {}", workflow.getWorkflowName());
            // Example: Ingest pet data and process orders (simulate)
            // On success:
            workflow.setStatus("COMPLETED");
            logger.info("Workflow {} completed successfully", workflow.getWorkflowName());
        } else {
            logger.error("Workflow {} is in invalid status: {}", workflow.getWorkflowName(), workflow.getStatus());
            workflow.setStatus("FAILED");
        }
    }

    private void processPetOrder(PetOrder petOrder) {
        logger.info("Processing PetOrder for petId: {}", petOrder.getPetId());
        // Validate pet availability (simulate by querying Pet entity)
        // Already checked in createPetOrder, so just approve here
        if (!"CANCELLED".equalsIgnoreCase(petOrder.getStatus())) {
            petOrder.setStatus("APPROVED");
            logger.info("PetOrder approved for petId: {}", petOrder.getPetId());
            // Additional: Could trigger notification here
        }
    }

    private void processPet(Pet pet) {
        logger.info("Processing Pet with petId: {}", pet.getPetId());
        if ("AVAILABLE".equalsIgnoreCase(pet.getStatus()) ||
                "PENDING".equalsIgnoreCase(pet.getStatus()) ||
                "SOLD".equalsIgnoreCase(pet.getStatus())) {
            logger.info("Pet {} status is valid: {}", pet.getName(), pet.getStatus());
        } else {
            logger.error("Pet {} has invalid status: {}", pet.getName(), pet.getStatus());
            pet.setStatus("AVAILABLE"); // default fallback
        }
        // Notify listeners or trigger events if needed
    }
}