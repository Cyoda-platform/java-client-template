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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/api")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    // ------------- Workflow Endpoints --------------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflow) {
        try {
            if (!workflow.isValid()) {
                log.error("Invalid Workflow entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    workflow
            );
            UUID technicalId = idFuture.get(5, TimeUnit.SECONDS);
            log.info("Workflow created with technicalId: {}", technicalId);
            // processWorkflow call removed
            return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Bad request creating Workflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error creating Workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Workflow.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get(5, TimeUnit.SECONDS);
            if (node == null || node.isEmpty()) {
                log.error("Workflow not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Workflow workflow = Workflow.fromJson(node);
            return ResponseEntity.ok(workflow);
        } catch (IllegalArgumentException e) {
            log.error("Bad request getting Workflow", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error getting Workflow", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------- Pet Endpoints --------------

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@RequestBody Pet pet) {
        try {
            if (!pet.isValid()) {
                log.error("Invalid Pet entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    pet
            );
            UUID technicalId = idFuture.get(5, TimeUnit.SECONDS);
            log.info("Pet created with technicalId: {}", technicalId);
            // processPet call removed
            return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Bad request creating Pet", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error creating Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get(5, TimeUnit.SECONDS);
            if (node == null || node.isEmpty()) {
                log.error("Pet not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Pet pet = Pet.fromJson(node);
            return ResponseEntity.ok(pet);
        } catch (IllegalArgumentException e) {
            log.error("Bad request getting Pet", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error getting Pet", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> getPetsByStatus(@RequestParam(value = "status", required = false) String status) {
        try {
            if (status == null) {
                // get all pets
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Pet.ENTITY_NAME,
                        ENTITY_VERSION
                );
                ArrayNode nodes = itemsFuture.get(5, TimeUnit.SECONDS);
                List<Pet> pets = new ArrayList<>();
                if (nodes != null) {
                    for (int i = 0; i < nodes.size(); i++) {
                        ObjectNode node = (ObjectNode) nodes.get(i);
                        Pet pet = Pet.fromJson(node);
                        pets.add(pet);
                    }
                }
                return ResponseEntity.ok(pets);
            }
            // filtered by status using getItemsByCondition with inMemory=true
            Condition condition = Condition.of("$.status", "IEQUALS", status);
            SearchConditionRequest scr = SearchConditionRequest.group("AND", condition);
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    scr,
                    true
            );
            ArrayNode filteredNodes = filteredItemsFuture.get(5, TimeUnit.SECONDS);
            List<Pet> filteredPets = new ArrayList<>();
            if (filteredNodes != null) {
                for (int i = 0; i < filteredNodes.size(); i++) {
                    ObjectNode node = (ObjectNode) filteredNodes.get(i);
                    Pet pet = Pet.fromJson(node);
                    filteredPets.add(pet);
                }
            }
            return ResponseEntity.ok(filteredPets);
        } catch (IllegalArgumentException e) {
            log.error("Bad request getting Pets", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error getting Pets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ------------- Order Endpoints --------------

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@RequestBody Order order) {
        try {
            if (!order.isValid()) {
                log.error("Invalid Order entity");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            // Validate petId exists by checking pet entityService
            UUID petIdUuid;
            try {
                petIdUuid = UUID.fromString(order.getPetId());
            } catch (IllegalArgumentException ex) {
                log.error("Invalid petId format in Order: {}", order.getPetId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("error", "Invalid petId format"));
            }
            CompletableFuture<ObjectNode> petFuture = entityService.getItem(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION,
                    petIdUuid
            );
            ObjectNode petNode = petFuture.get(5, TimeUnit.SECONDS);
            if (petNode == null || petNode.isEmpty()) {
                log.error("Order creation failed: PetId {} does not exist", order.getPetId());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("error", "Invalid petId"));
            }

            CompletableFuture<UUID> idFuture = entityService.addItem(
                    Order.ENTITY_NAME,
                    ENTITY_VERSION,
                    order
            );
            UUID technicalId = idFuture.get(5, TimeUnit.SECONDS);
            log.info("Order created with technicalId: {}", technicalId);
            // processOrder call removed
            return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId.toString()));
        } catch (IllegalArgumentException e) {
            log.error("Bad request creating Order", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error creating Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable("id") String id) {
        try {
            UUID technicalId = UUID.fromString(id);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(
                    Order.ENTITY_NAME,
                    ENTITY_VERSION,
                    technicalId
            );
            ObjectNode node = itemFuture.get(5, TimeUnit.SECONDS);
            if (node == null || node.isEmpty()) {
                log.error("Order not found with id: {}", id);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            Order order = Order.fromJson(node);
            return ResponseEntity.ok(order);
        } catch (IllegalArgumentException e) {
            log.error("Bad request getting Order", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error getting Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrdersByStatus(@RequestParam(value = "status", required = false) String status) {
        try {
            if (status == null) {
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                        Order.ENTITY_NAME,
                        ENTITY_VERSION
                );
                ArrayNode nodes = itemsFuture.get(5, TimeUnit.SECONDS);
                List<Order> orders = new ArrayList<>();
                if (nodes != null) {
                    for (int i = 0; i < nodes.size(); i++) {
                        ObjectNode node = (ObjectNode) nodes.get(i);
                        Order order = Order.fromJson(node);
                        orders.add(order);
                    }
                }
                return ResponseEntity.ok(orders);
            }
            Condition condition = Condition.of("$.status", "IEQUALS", status);
            SearchConditionRequest scr = SearchConditionRequest.group("AND", condition);
            CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition(
                    Order.ENTITY_NAME,
                    ENTITY_VERSION,
                    scr,
                    true
            );
            ArrayNode filteredNodes = filteredItemsFuture.get(5, TimeUnit.SECONDS);
            List<Order> filteredOrders = new ArrayList<>();
            if (filteredNodes != null) {
                for (int i = 0; i < filteredNodes.size(); i++) {
                    ObjectNode node = (ObjectNode) filteredNodes.get(i);
                    Order order = Order.fromJson(node);
                    filteredOrders.add(order);
                }
            }
            return ResponseEntity.ok(filteredOrders);
        } catch (IllegalArgumentException e) {
            log.error("Bad request getting Orders", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error getting Orders", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
