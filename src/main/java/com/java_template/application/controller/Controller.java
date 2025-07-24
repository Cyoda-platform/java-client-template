package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import jakarta.validation.Valid;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/main")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // --- Workflow endpoints ---

    @PostMapping("/workflows")
    public ResponseEntity<?> createWorkflow(@Valid @RequestBody Workflow workflow) throws JsonProcessingException {
        try {
            if (workflow == null || !workflow.isValid()) {
                logger.error("Invalid Workflow creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid workflow data"));
            }
            CompletableFuture<UUID> idFuture = entityService.addItem("Workflow", ENTITY_VERSION, workflow);
            UUID technicalId = idFuture.get();

            logger.info("Created Workflow with technicalId: {}", technicalId);

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
    public ResponseEntity<?> getWorkflow(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Workflow", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Workflow not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
            }
            Workflow workflow = objectMapper.treeToValue(node, Workflow.class);
            return ResponseEntity.ok(workflow);
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
    public ResponseEntity<?> createPetOrder(@Valid @RequestBody PetOrder petOrder) throws JsonProcessingException {
        try {
            if (petOrder == null || !petOrder.isValid()) {
                logger.error("Invalid PetOrder creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet order data"));
            }

            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.petId", "EQUALS", petOrder.getPetId()),
                    Condition.of("$.status", "IEQUALS", "AVAILABLE"));
            CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
            ArrayNode pets = petsFuture.get();

            if (pets == null || pets.size() == 0) {
                logger.error("Pet with ID {} is not available for order", petOrder.getPetId());
                petOrder.setStatus("CANCELLED");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet not available for order"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem("PetOrder", ENTITY_VERSION, petOrder);
            UUID technicalId = idFuture.get();

            logger.info("Created PetOrder with technicalId: {}", technicalId);

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
    public ResponseEntity<?> getPetOrder(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("PetOrder", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("PetOrder not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "PetOrder not found"));
            }
            PetOrder petOrder = objectMapper.treeToValue(node, PetOrder.class);
            return ResponseEntity.ok(petOrder);
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
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws JsonProcessingException {
        try {
            if (pet == null || !pet.isValid()) {
                logger.error("Invalid Pet creation request");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet data"));
            }
            CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
            UUID technicalId = idFuture.get();
            logger.info("Created Pet with technicalId: {}", technicalId);

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
    public ResponseEntity<?> getPet(@PathVariable String id) throws JsonProcessingException {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("Pet", ENTITY_VERSION, technicalId);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Pet not found with technicalId: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
            }
            Pet pet = objectMapper.treeToValue(node, Pet.class);
            return ResponseEntity.ok(pet);
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
}