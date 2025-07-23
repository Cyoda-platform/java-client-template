package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;
import static com.java_template.common.config.Config.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/petsOrders")
@Slf4j
public class Controller {

    private final EntityService entityService;

    private static final String PET_ENTITY = "Pet";
    private static final String PET_ORDER_ENTITY = "PetOrder";

    public Controller(EntityService entityService) {
        this.entityService = entityService;
    }

    // POST /petsOrders/pets - Create new Pet
    @PostMapping("/pets")
    public CompletableFuture<Pet> createPet(@RequestBody Pet petRequest) {
        if (petRequest == null) {
            log.error("Pet creation failed: Request body is null");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet data is required");
        }
        if (petRequest.getName() == null || petRequest.getName().isBlank()) {
            log.error("Pet creation failed: Name is blank");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet name is required");
        }
        if (petRequest.getType() == null || petRequest.getType().isBlank()) {
            log.error("Pet creation failed: Type is blank");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet type is required");
        }

        petRequest.setStatus("AVAILABLE");

        // Add Pet entity via EntityService
        return entityService.addItem(PET_ENTITY, ENTITY_VERSION, petRequest)
                .thenCompose(technicalId -> entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId))
                .thenApply(objectNode -> {
                    Pet pet = JsonUtil.toPet(objectNode);
                    log.info("Created Pet with technicalId: {}", technicalIdFromObjectNode(objectNode));
                    return pet;
                });
    }

    // GET /petsOrders/pets/{technicalId} - Retrieve Pet by technicalId
    @GetMapping("/pets/{technicalId}")
    public CompletableFuture<Pet> getPet(@PathVariable UUID technicalId) {
        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("Pet not found with technicalId: {}", technicalId);
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
                    }
                    return JsonUtil.toPet(objectNode);
                });
    }

    // POST /petsOrders/orders - Create new PetOrder
    @PostMapping("/orders")
    public CompletableFuture<PetOrder> createPetOrder(@RequestBody PetOrder orderRequest) {
        if (orderRequest == null) {
            log.error("Order creation failed: Request body is null");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Order data is required");
        }
        if (orderRequest.getCustomerName() == null || orderRequest.getCustomerName().isBlank()) {
            log.error("Order creation failed: Customer name is blank");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Customer name is required");
        }
        if (orderRequest.getPetTechnicalId() == null) {
            log.error("Order creation failed: Pet technicalId is null");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet technicalId is required");
        }
        if (orderRequest.getQuantity() == null || orderRequest.getQuantity() < 1) {
            log.error("Order creation failed: Quantity invalid");
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Quantity must be at least 1");
        }

        // Validate pet availability
        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, orderRequest.getPetTechnicalId())
                .thenCompose(petNode -> {
                    if (petNode == null || petNode.isEmpty()) {
                        log.error("Order failed: Pet not found with technicalId: {}", orderRequest.getPetTechnicalId());
                        orderRequest.setStatus("FAILED");
                        return CompletableFuture.completedFuture(orderRequest);
                    }
                    String petStatus = petNode.has("status") && !petNode.get("status").isNull() ?
                            petNode.get("status").asText() : null;
                    if (!"AVAILABLE".equalsIgnoreCase(petStatus)) {
                        log.error("Order failed: Pet with technicalId {} not available", orderRequest.getPetTechnicalId());
                        orderRequest.setStatus("FAILED");
                        return CompletableFuture.completedFuture(orderRequest);
                    }
                    if (orderRequest.getQuantity() == null || orderRequest.getQuantity() < 1) {
                        log.error("Order failed: Invalid quantity for order");
                        orderRequest.setStatus("FAILED");
                        return CompletableFuture.completedFuture(orderRequest);
                    }

                    // Create new PetOrder with status PENDING and add it
                    orderRequest.setStatus("PENDING");
                    return entityService.addItem(PET_ORDER_ENTITY, ENTITY_VERSION, orderRequest)
                            .thenCompose(technicalId -> {
                                // Mark pet as SOLD by creating new Pet version with updated status
                                Pet updatedPet = JsonUtil.toPet(petNode);
                                updatedPet.setStatus("SOLD");
                                return entityService.addItem(PET_ENTITY, ENTITY_VERSION, updatedPet)
                                        .thenCompose(newPetTechnicalId -> {
                                            // Update order status to COMPLETED by creating a new PetOrder version
                                            orderRequest.setStatus("COMPLETED");
                                            orderRequest.setPetTechnicalId(orderRequest.getPetTechnicalId());
                                            return entityService.addItem(PET_ORDER_ENTITY, ENTITY_VERSION, orderRequest)
                                                    .thenCompose(orderCompletedTechnicalId ->
                                                            entityService.getItem(PET_ORDER_ENTITY, ENTITY_VERSION, orderCompletedTechnicalId))
                                                    .thenApply(orderCompletedNode -> {
                                                        PetOrder completedOrder = JsonUtil.toPetOrder(orderCompletedNode);
                                                        log.info("Order {} completed successfully for pet technicalId {}", 
                                                                orderCompletedTechnicalId, orderRequest.getPetTechnicalId());
                                                        return completedOrder;
                                                    });
                                        });
                            });
                });
    }

    // GET /petsOrders/orders/{technicalId} - Retrieve PetOrder by technicalId
    @GetMapping("/orders/{technicalId}")
    public CompletableFuture<PetOrder> getPetOrder(@PathVariable UUID technicalId) {
        return entityService.getItem(PET_ORDER_ENTITY, ENTITY_VERSION, technicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        log.error("Order not found with technicalId: {}", technicalId);
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "Order not found");
                    }
                    return JsonUtil.toPetOrder(objectNode);
                });
    }

    private UUID technicalIdFromObjectNode(ObjectNode objectNode) {
        if (objectNode.has("technicalId") && !objectNode.get("technicalId").isNull()) {
            return UUID.fromString(objectNode.get("technicalId").asText());
        }
        return null;
    }

    // Utility class for JSON conversions between ObjectNode and entity classes
    private static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static Pet toPet(ObjectNode node) {
            try {
                Pet pet = mapper.treeToValue(node, Pet.class);
                if (node.has("technicalId") && !node.get("technicalId").isNull()) {
                    pet.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                }
                return pet;
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert ObjectNode to Pet", e);
            }
        }

        static PetOrder toPetOrder(ObjectNode node) {
            try {
                PetOrder order = mapper.treeToValue(node, PetOrder.class);
                if (node.has("technicalId") && !node.get("technicalId").isNull()) {
                    order.setTechnicalId(UUID.fromString(node.get("technicalId").asText()));
                }
                return order;
            } catch (Exception e) {
                throw new RuntimeException("Failed to convert ObjectNode to PetOrder", e);
            }
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pet {
        private UUID technicalId;
        private String petId; // deprecated, kept for compatibility
        private String name;
        private String type;
        private String status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetOrder {
        private UUID technicalId;
        private UUID petTechnicalId; // reference to Pet by technicalId
        private String orderId; // deprecated, kept for compatibility
        private String customerName;
        private Integer quantity;
        private String status;
    }
}