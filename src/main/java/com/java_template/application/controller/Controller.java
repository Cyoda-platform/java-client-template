package com.java_template.application.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.UUID;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.Order;

import com.java_template.common.service.EntityService;
import static com.java_template.common.config.Config.*;

import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;

@RestController
@RequestMapping(path = "/api")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    // ----------- WORKFLOW ENDPOINTS -----------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (!workflow.isValid()) {
                log.error("Invalid Workflow entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Workflow.ENTITY_NAME, ENTITY_VERSION, workflow);
            UUID technicalId = idFuture.get();

            log.info("Workflow created with technicalId: {}", technicalId.toString());
            //processWorkflow(technicalId.toString(), workflow);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException creating Workflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error creating Workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/workflows/{technicalId}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Workflow.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("Workflow not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Workflow workflow = Workflow.fromObjectNode(node);
            return ResponseEntity.ok(workflow);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Workflow technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                log.error("Workflow not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            log.error("Error retrieving Workflow", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Error retrieving Workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------- PET ENDPOINTS -----------

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        try {
            if (!pet.isValid()) {
                log.error("Invalid Pet entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Pet.ENTITY_NAME, ENTITY_VERSION, pet);
            UUID technicalId = idFuture.get();

            log.info("Pet created with technicalId: {}", technicalId.toString());
            //processPet(technicalId.toString(), pet);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException creating Pet", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/pets/{technicalId}")
    public ResponseEntity<Pet> getPet(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Pet.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("Pet not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Pet pet = Pet.fromObjectNode(node);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Pet technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                log.error("Pet not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            log.error("Error retrieving Pet", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Error retrieving Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ----------- ORDER ENDPOINTS -----------

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        try {
            if (!order.isValid()) {
                log.error("Invalid Order entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Order.ENTITY_NAME, ENTITY_VERSION, order);
            UUID technicalId = idFuture.get();

            log.info("Order created with technicalId: {}", technicalId.toString());
            //processOrder(technicalId.toString(), order);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException creating Order", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error creating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/orders/{technicalId}")
    public ResponseEntity<Order> getOrder(@PathVariable String technicalId) {
        try {
            UUID uuid = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Order.ENTITY_NAME, ENTITY_VERSION, uuid);
            ObjectNode node = itemFuture.get();
            if (node == null) {
                log.error("Order not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Order order = Order.fromObjectNode(node);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for Order technicalId: {}", technicalId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof NoSuchElementException) {
                log.error("Order not found for technicalId: {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            log.error("Error retrieving Order", ee);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Error retrieving Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}