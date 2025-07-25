package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Order;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;

    // --- Workflow endpoints ---

    @PostMapping("/workflows")
    public ResponseEntity<?> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (workflow == null) {
                logger.error("Workflow creation failed: request body is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Workflow data is required"));
            }
            if (workflow.getName() == null || workflow.getName().isBlank()) {
                logger.error("Workflow creation failed: name is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Workflow name is required"));
            }
            if (workflow.getPetStoreApiUrl() == null || workflow.getPetStoreApiUrl().isBlank()) {
                logger.error("Workflow creation failed: petStoreApiUrl is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "PetStoreApiUrl is required"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "workflow",
                    ENTITY_VERSION,
                    workflow
            );
            UUID technicalId = idFuture.get();

            String techIdStr = "workflow-" + technicalId.toString();
            logger.info("Created Workflow with technicalId: {}", techIdStr);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Workflow creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Workflow creation error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/workflows/{technicalId}")
    public ResponseEntity<?> getWorkflow(@PathVariable String technicalId) {
        try {
            // Extract UUID technicalId from string like "workflow-<UUID>"
            if (!technicalId.startsWith("workflow-")) {
                logger.error("Invalid technicalId format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
            }
            UUID uuid = UUID.fromString(technicalId.substring("workflow-".length()));

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("workflow", ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Workflow not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Get Workflow failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Get Workflow error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Pet endpoints ---

    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@RequestBody Pet pet) {
        try {
            if (pet == null) {
                logger.error("Pet creation failed: request body is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet data is required"));
            }
            if (pet.getName() == null || pet.getName().isBlank()) {
                logger.error("Pet creation failed: name is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet name is required"));
            }
            if (pet.getCategory() == null || pet.getCategory().isBlank()) {
                logger.error("Pet creation failed: category is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet category is required"));
            }
            if (pet.getStatus() == null || pet.getStatus().isBlank()) {
                logger.error("Pet creation failed: status is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet status is required"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "pet",
                    ENTITY_VERSION,
                    pet
            );
            UUID technicalId = idFuture.get();

            String techIdStr = "pet-" + technicalId.toString();
            logger.info("Created Pet with technicalId: {}", techIdStr);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Pet creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Pet creation error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<?> getPet(@PathVariable String technicalId) {
        try {
            if (!technicalId.startsWith("pet-")) {
                logger.error("Invalid technicalId format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
            }
            UUID uuid = UUID.fromString(technicalId.substring("pet-".length()));

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("pet", ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Pet not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Get Pet failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Get Pet error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // --- Order endpoints ---

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Order order) {
        try {
            if (order == null) {
                logger.error("Order creation failed: request body is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Order data is required"));
            }
            if (order.getPetId() == null) {
                logger.error("Order creation failed: petId is null");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "PetId is required"));
            }
            if (order.getQuantity() == null || order.getQuantity() <= 0) {
                logger.error("Order creation failed: quantity invalid");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Quantity must be positive"));
            }
            if (order.getStatus() == null || order.getStatus().isBlank()) {
                logger.error("Order creation failed: status is blank");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Order status is required"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    "order",
                    ENTITY_VERSION,
                    order
            );
            UUID technicalId = idFuture.get();

            String techIdStr = "order-" + technicalId.toString();
            logger.info("Created Order with technicalId: {}", techIdStr);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Order creation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Order creation error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/orders/{technicalId}")
    public ResponseEntity<?> getOrder(@PathVariable String technicalId) {
        try {
            if (!technicalId.startsWith("order-")) {
                logger.error("Invalid technicalId format: {}", technicalId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid technicalId format"));
            }
            UUID uuid = UUID.fromString(technicalId.substring("order-".length()));

            CompletableFuture<ObjectNode> itemFuture = entityService.getItem("order", ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                logger.error("Order not found: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
            }
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            logger.error("Get Order failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Get Order error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }
}