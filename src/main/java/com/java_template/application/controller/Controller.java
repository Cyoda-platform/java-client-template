package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PetOrder;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/petsOrders")
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    private static final String PET_ENTITY = "pet";
    private static final String PET_ORDER_ENTITY = "petOrder";

    public Controller(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    // POST /petsOrders/pets - Create new Pet
    @PostMapping("/pets")
    public CompletableFuture<Pet> createPet(@Valid @RequestBody Pet petRequest) throws JsonProcessingException {
        if (petRequest == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet data is required");
        }
        if (petRequest.getName() == null || petRequest.getName().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet name is required");
        }
        if (petRequest.getType() == null || petRequest.getType().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet type is required");
        }

        petRequest.setStatus("AVAILABLE");

        // Add Pet entity via EntityService
        return entityService.addItem(PET_ENTITY, ENTITY_VERSION, petRequest)
                .thenCompose(technicalId -> entityService.getItem(PET_ENTITY, ENTITY_VERSION, technicalId))
                .thenApply(objectNode -> {
                    try {
                        Pet pet = objectMapper.treeToValue(objectNode, Pet.class);
                        if (objectNode.has("technicalId") && !objectNode.get("technicalId").isNull()) {
                            pet.setTechnicalId(UUID.fromString(objectNode.get("technicalId").asText()));
                        }
                        return pet;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Failed to convert ObjectNode to Pet", e);
                    }
                });
    }

    // GET /petsOrders/pets/{technicalId} - Retrieve Pet by technicalId
    @GetMapping("/pets/{technicalId}")
    public CompletableFuture<Pet> getPet(@PathVariable String technicalId) throws JsonProcessingException {
        UUID uuidTechnicalId = UUID.fromString(technicalId);
        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, uuidTechnicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "Pet not found");
                    }
                    try {
                        Pet pet = objectMapper.treeToValue(objectNode, Pet.class);
                        if (objectNode.has("technicalId") && !objectNode.get("technicalId").isNull()) {
                            pet.setTechnicalId(UUID.fromString(objectNode.get("technicalId").asText()));
                        }
                        return pet;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Failed to convert ObjectNode to Pet", e);
                    }
                });
    }

    // POST /petsOrders/orders - Create new PetOrder
    @PostMapping("/orders")
    public CompletableFuture<PetOrder> createPetOrder(@Valid @RequestBody PetOrder orderRequest) throws JsonProcessingException {
        if (orderRequest == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Order data is required");
        }
        if (orderRequest.getCustomerName() == null || orderRequest.getCustomerName().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Customer name is required");
        }
        if (orderRequest.getPetId() == null || orderRequest.getPetId().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet business ID is required");
        }
        if (orderRequest.getQuantity() == null || orderRequest.getQuantity() < 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Quantity must be at least 1");
        }

        // Validate pet availability by technicalId - must get UUID from pet business ID
        // Since PetOrder has petId (business ID), we need to fetch by pet business ID or adapt logic
        // Assuming petRequest has technicalId provided for order creation (fixing code to use technicalId)

        // Here we assume PetOrder has petTechnicalId for reference, but entity class has petId (String)
        // We must resolve petTechnicalId UUID by petId business ID - this logic is missing, so we simplify:
        // For this fix, we assume petTechnicalId is provided as UUID in request (adjust entity or DTO accordingly)
        // Let's adapt PetOrder to have petTechnicalId as UUID for this controller usage

        // We'll use petTechnicalId from the request - this requires adjusting PetOrder class or mapping
        // So we do a conversion from petId (business ID) to technicalId - but no method provided, so we require petTechnicalId in request

        UUID petTechnicalId = orderRequest.getPetTechnicalId();
        if (petTechnicalId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Pet technicalId is required");
        }

        return entityService.getItem(PET_ENTITY, ENTITY_VERSION, petTechnicalId)
                .thenCompose(petNode -> {
                    if (petNode == null || petNode.isEmpty()) {
                        orderRequest.setStatus("FAILED");
                        return CompletableFuture.completedFuture(orderRequest);
                    }
                    String petStatus = petNode.has("status") && !petNode.get("status").isNull() ?
                            petNode.get("status").asText() : null;
                    if (!"AVAILABLE".equalsIgnoreCase(petStatus)) {
                        orderRequest.setStatus("FAILED");
                        return CompletableFuture.completedFuture(orderRequest);
                    }
                    if (orderRequest.getQuantity() == null || orderRequest.getQuantity() < 1) {
                        orderRequest.setStatus("FAILED");
                        return CompletableFuture.completedFuture(orderRequest);
                    }

                    // Create new PetOrder with status PENDING and add it
                    orderRequest.setStatus("PENDING");
                    return entityService.addItem(PET_ORDER_ENTITY, ENTITY_VERSION, orderRequest)
                            .thenCompose(technicalId -> {
                                // Mark pet as SOLD by creating new Pet version with updated status
                                Pet updatedPet;
                                try {
                                    updatedPet = objectMapper.treeToValue(petNode, Pet.class);
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException("Failed to convert ObjectNode to Pet", e);
                                }
                                updatedPet.setStatus("SOLD");
                                return entityService.addItem(PET_ENTITY, ENTITY_VERSION, updatedPet)
                                        .thenCompose(newPetTechnicalId -> {
                                            // Update order status to COMPLETED by creating a new PetOrder version
                                            orderRequest.setStatus("COMPLETED");
                                            orderRequest.setPetTechnicalId(petTechnicalId);
                                            return entityService.addItem(PET_ORDER_ENTITY, ENTITY_VERSION, orderRequest)
                                                    .thenCompose(orderCompletedTechnicalId ->
                                                            entityService.getItem(PET_ORDER_ENTITY, ENTITY_VERSION, orderCompletedTechnicalId))
                                                    .thenApply(orderCompletedNode -> {
                                                        try {
                                                            PetOrder completedOrder = objectMapper.treeToValue(orderCompletedNode, PetOrder.class);
                                                            if (orderCompletedNode.has("technicalId") && !orderCompletedNode.get("technicalId").isNull()) {
                                                                completedOrder.setTechnicalId(UUID.fromString(orderCompletedNode.get("technicalId").asText()));
                                                            }
                                                            return completedOrder;
                                                        } catch (JsonProcessingException e) {
                                                            throw new RuntimeException("Failed to convert ObjectNode to PetOrder", e);
                                                        }
                                                    });
                                        });
                            });
                });
    }

    // GET /petsOrders/orders/{technicalId} - Retrieve PetOrder by technicalId
    @GetMapping("/orders/{technicalId}")
    public CompletableFuture<PetOrder> getPetOrder(@PathVariable String technicalId) throws JsonProcessingException {
        UUID uuidTechnicalId = UUID.fromString(technicalId);
        return entityService.getItem(PET_ORDER_ENTITY, ENTITY_VERSION, uuidTechnicalId)
                .thenApply(objectNode -> {
                    if (objectNode == null || objectNode.isEmpty()) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "Order not found");
                    }
                    try {
                        PetOrder order = objectMapper.treeToValue(objectNode, PetOrder.class);
                        if (objectNode.has("technicalId") && !objectNode.get("technicalId").isNull()) {
                            order.setTechnicalId(UUID.fromString(objectNode.get("technicalId").asText()));
                        }
                        return order;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Failed to convert ObjectNode to PetOrder", e);
                    }
                });
    }
}