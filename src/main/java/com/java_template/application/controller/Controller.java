package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Order;
import com.java_template.application.entity.PetRegistrationJob;
import com.java_template.application.entity.Pet;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/main")
@Slf4j
@RequiredArgsConstructor
public class Controller {

    private final EntityService entityService;

    private static final String PET_REGISTRATION_JOB_MODEL = "PetRegistrationJob";
    private static final String PET_MODEL = "Pet";
    private static final String ORDER_MODEL = "Order";

    // ----------- PetRegistrationJob endpoints -----------

    @PostMapping("/pet-registration-jobs")
    public ResponseEntity<?> createPetRegistrationJob(@RequestBody PetRegistrationJob job) {
        try {
            if (job == null || job.getPetName() == null || job.getPetName().isBlank()
                    || job.getPetType() == null || job.getPetType().isBlank()
                    || job.getPetStatus() == null || job.getPetStatus().isBlank()
                    || job.getOwnerName() == null || job.getOwnerName().isBlank()) {
                log.error("Invalid PetRegistrationJob input");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Missing required fields"));
            }

            job.setStatus("PENDING");
            job.setCreatedAt(java.time.LocalDateTime.now());

            CompletableFuture<UUID> idFuture = entityService.addItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, job);
            UUID technicalId = idFuture.get();

            log.info("Created PetRegistrationJob with ID: {}", technicalId);

            CompletableFuture<ArrayNode> petsFuture = entityService.getItems(PET_MODEL, ENTITY_VERSION);
            ArrayNode petsNode = petsFuture.get();

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString(), "pets", petsNode));
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error processing PetRegistrationJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Processing failed"));
        }
    }

    @GetMapping("/pet-registration-jobs/{id}")
    public ResponseEntity<?> getPetRegistrationJob(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_REGISTRATION_JOB_MODEL, ENTITY_VERSION, technicalId);
            ObjectNode jobNode = itemFuture.get();

            if (jobNode == null || jobNode.isEmpty()) {
                log.error("PetRegistrationJob not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            return ResponseEntity.ok(jobNode);
        } catch (IllegalArgumentException e) {
            log.error("Invalid PetRegistrationJob ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving PetRegistrationJob: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error retrieving data"));
        }
    }

    // ----------- Pet endpoints -----------

    @GetMapping("/pets")
    public ResponseEntity<?> getPets() {
        try {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(PET_MODEL, ENTITY_VERSION);
            ArrayNode petsNode = itemsFuture.get();
            return ResponseEntity.ok(petsNode);
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving pets: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error retrieving pets"));
        }
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPet(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(PET_MODEL, ENTITY_VERSION, technicalId);
            ObjectNode petNode = itemFuture.get();
            if (petNode == null || petNode.isEmpty()) {
                log.error("Pet not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(petNode);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Pet ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving pet: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error retrieving pet"));
        }
    }

    // ----------- Order endpoints -----------

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody Order order) {
        try {
            if (order == null || order.getPetId() == null || order.getPetId().isBlank()
                    || order.getQuantity() == null || order.getQuantity() <= 0
                    || order.getStatus() == null || order.getStatus().isBlank()) {
                log.error("Invalid Order input");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Missing or invalid required fields"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(ORDER_MODEL, ENTITY_VERSION, order);
            UUID technicalId = idFuture.get();

            log.info("Created Order with ID: {}", technicalId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error processing Order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Processing failed"));
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ORDER_MODEL, ENTITY_VERSION, technicalId);
            ObjectNode orderNode = itemFuture.get();
            if (orderNode == null || orderNode.isEmpty()) {
                log.error("Order not found for ID: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(orderNode);
        } catch (IllegalArgumentException e) {
            log.error("Invalid Order ID format: {}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid ID format"));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error retrieving Order: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error retrieving Order"));
        }
    }
}
