package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.Valid;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/api")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // ------------- Workflow Endpoints --------------

    @PostMapping("/workflows")
    public ResponseEntity<Map<String, String>> createWorkflow(@Valid @RequestBody Workflow workflow) throws JsonProcessingException, ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
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
        return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId.toString()));
    }

    @GetMapping("/workflows/{id}")
    public ResponseEntity<Workflow> getWorkflow(@PathVariable("id") String id) throws JsonProcessingException, ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
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
        Workflow workflow = objectMapper.treeToValue(node, Workflow.class);
        return ResponseEntity.ok(workflow);
    }

    // ------------- Pet Endpoints --------------

    @PostMapping("/pets")
    public ResponseEntity<Map<String, String>> createPet(@Valid @RequestBody Pet pet) throws JsonProcessingException, ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
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
        return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId.toString()));
    }

    @GetMapping("/pets/{id}")
    public ResponseEntity<Pet> getPet(@PathVariable("id") String id) throws JsonProcessingException, ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
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
        Pet pet = objectMapper.treeToValue(node, Pet.class);
        return ResponseEntity.ok(pet);
    }

    @GetMapping("/pets")
    public ResponseEntity<List<Pet>> getPetsByStatus(@RequestParam(value = "status", required = false) String status) throws JsonProcessingException, ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
        if (status == null) {
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItems(
                    Pet.ENTITY_NAME,
                    ENTITY_VERSION
            );
            ArrayNode nodes = itemsFuture.get(5, TimeUnit.SECONDS);
            List<Pet> pets = new ArrayList<>();
            if (nodes != null) {
                for (int i = 0; i < nodes.size(); i++) {
                    ObjectNode node = (ObjectNode) nodes.get(i);
                    Pet pet = objectMapper.treeToValue(node, Pet.class);
                    pets.add(pet);
                }
            }
            return ResponseEntity.ok(pets);
        }
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
                Pet pet = objectMapper.treeToValue(node, Pet.class);
                filteredPets.add(pet);
            }
        }
        return ResponseEntity.ok(filteredPets);
    }

    // ------------- Order Endpoints --------------

    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> createOrder(@Valid @RequestBody Order order) throws JsonProcessingException, ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
        if (!order.isValid()) {
            log.error("Invalid Order entity");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
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
        return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId.toString()));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable("id") String id) throws JsonProcessingException, ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
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
        Order order = objectMapper.treeToValue(node, Order.class);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrdersByStatus(@RequestParam(value = "status", required = false) String status) throws JsonProcessingException, ExecutionException, InterruptedException, java.util.concurrent.TimeoutException {
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
                    Order order = objectMapper.treeToValue(node, Order.class);
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
                Order order = objectMapper.treeToValue(node, Order.class);
                filteredOrders.add(order);
            }
        }
        return ResponseEntity.ok(filteredOrders);
    }
}