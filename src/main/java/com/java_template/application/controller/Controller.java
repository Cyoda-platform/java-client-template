package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Workflow;
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
@RequestMapping(path = "/controller")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // --- Workflow Endpoints ---

    @PostMapping("/workflows")
    public ResponseEntity<?> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (workflow == null || !workflow.isValid()) {
                logger.error("Invalid Workflow entity data received.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Workflow data"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    workflow
            );
            UUID technicalId = idFuture.get();

            logger.info("Created Workflow with technicalId: {}", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in createWorkflow: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal server error in createWorkflow: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/workflows/{technicalId}")
    public ResponseEntity<?> getWorkflowById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Workflow not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
            }
            // Convert ObjectNode to Workflow
            Workflow workflow = new Workflow();
            workflow = Workflow.fromJson(node); // Assuming fromJson method exists, otherwise map manually

            logger.info("Retrieved Workflow with technicalId: {}", technicalId);
            return ResponseEntity.ok(workflow);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (Exception e) {
            logger.error("Internal server error in getWorkflowById: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }

    }

    // --- Pet Endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        try {
            if (pet == null || !pet.isValid()) {
                logger.error("Invalid Pet entity data received.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Pet data"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    pet
            );
            UUID technicalId = idFuture.get();

            logger.info("Created Pet with technicalId: {}", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in createPet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal server error in createPet: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<?> getPetById(@PathVariable String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    id
            );
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Pet not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
            }
            Pet pet = Pet.fromJson(node); // Assuming fromJson method exists, else map manually

            logger.info("Retrieved Pet with technicalId: {}", technicalId);
            return ResponseEntity.ok(pet);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid UUID format for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
        } catch (Exception e) {
            logger.error("Internal server error in getPetById: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/pets")
    public ResponseEntity<?> getPetsByStatus(@RequestParam(required = false) String status) {
        try {
            if (status == null || status.isBlank()) {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Pet.ENTITY_NAME,
                        ENTITY_VERSION
                );
                ArrayNode arrayNode = itemsFuture.get();

                List<Pet> result = new ArrayList<>();
                for (var jsonNode : arrayNode) {
                    Pet pet = Pet.fromJson((ObjectNode) jsonNode);
                    result.add(pet);
                }
                logger.info("Retrieved {} pets with no status filter", result.size());
                return ResponseEntity.ok(result);
            } else {
                Condition condition = Condition.of("$.status", "IEQUALS", status);
                SearchConditionRequest searchCondition = SearchConditionRequest.group("AND", condition);

                CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                        Pet.ENTITY_NAME,
                        ENTITY_VERSION,
                        searchCondition,
                        true
                );
                ArrayNode arrayNode = filteredItemsFuture.get();

                List<Pet> result = new ArrayList<>();
                for (var jsonNode : arrayNode) {
                    Pet pet = Pet.fromJson((ObjectNode) jsonNode);
                    result.add(pet);
                }
                logger.info("Retrieved {} pets with status '{}'", result.size(), status);
                return ResponseEntity.ok(result);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Bad request in getPetsByStatus: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Internal server error in getPetsByStatus: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

}