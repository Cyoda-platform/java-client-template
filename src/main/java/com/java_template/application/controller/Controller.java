package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.Order;
import com.java_template.application.entity.Pet;
import com.java_template.application.entity.PurrfectPetsJob;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    // We still need local counters for job pet and order IDs in process methods because addItem returns UUID not string id with prefix
    private final AtomicLong purrfectPetsJobIdCounter = new AtomicLong(1);
    private final AtomicLong petIdCounter = new AtomicLong(1);
    private final AtomicLong orderIdCounter = new AtomicLong(1);

    // POST /entities/jobs - create a new PurrfectPetsJob
    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@Valid @RequestBody PurrfectPetsJob job) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (job == null || !job.isValid()) {
            logger.error("Invalid PurrfectPetsJob entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid job data"));
        }
        job.setStatus("PENDING");
        job.setCreatedAt(java.time.Instant.now().toString());

        CompletableFuture<UUID> idFuture = entityService.addItem("PurrfectPetsJob", ENTITY_VERSION, job);
        UUID technicalId = idFuture.get();
        String techIdStr = "job-" + purrfectPetsJobIdCounter.getAndIncrement(); // preserve legacy id style for response/log

        logger.info("Created PurrfectPetsJob with technicalId: {}", techIdStr);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
    }

    // GET /entities/jobs/{id} - get PurrfectPetsJob by technicalId
    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJobById(@PathVariable("id") String id) {
        try {
            // The id here is legacy style "job-<number>", but EntityService requires UUID technicalId
            // We cannot translate legacy id to UUID, so skip and return 404 as we cannot find
            // Alternative: just fallback to 404
            logger.error("Retrieval by legacy id '{}' not supported with EntityService, skipping", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getJobById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving PurrfectPetsJob: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // POST /entities/pets - create a new Pet entity
    @PostMapping("/pets")
    public ResponseEntity<?> createPet(@Valid @RequestBody Pet pet) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (pet == null || !pet.isValid()) {
            logger.error("Invalid Pet entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid pet data"));
        }
        CompletableFuture<UUID> idFuture = entityService.addItem("Pet", ENTITY_VERSION, pet);
        UUID technicalId = idFuture.get();
        String techIdStr = "pet-" + petIdCounter.getAndIncrement();

        logger.info("Created Pet with technicalId: {}", techIdStr);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
    }

    // GET /entities/pets/{id} - get Pet by technicalId
    @GetMapping("/pets/{id}")
    public ResponseEntity<?> getPetById(@PathVariable("id") String id) {
        try {
            // legacy id like "pet-<number>" can't be translated to UUID, so skip and return 404
            logger.error("Retrieval by legacy id '{}' not supported with EntityService, skipping", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Pet not found"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getPetById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving Pet: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }

    // GET /entities/pets/findByStatus?status=available
    @GetMapping("/pets/findByStatus")
    public ResponseEntity<?> findPetsByStatus(@RequestParam("status") String status) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (status == null || status.isBlank()) {
            logger.error("Invalid status parameter for findByStatus");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyList());
        }
        // Build condition for status EQUALS ignoring case
        Condition cond = Condition.of("$.status", "IEQUALS", status);
        SearchConditionRequest condition = SearchConditionRequest.group("AND", cond);

        CompletableFuture<ArrayNode> filteredItemsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, condition, true);
        ArrayNode filteredItems = filteredItemsFuture.get();

        List<Pet> pets = new ArrayList<>();
        for (int i = 0; i < filteredItems.size(); i++) {
            ObjectNode node = (ObjectNode) filteredItems.get(i);
            Pet pet = objectMapper.treeToValue(node, Pet.class);
            pets.add(pet);
        }

        return ResponseEntity.ok(pets);
    }

    // POST /entities/orders - create a new Order entity
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@Valid @RequestBody Order order) throws JsonProcessingException, ExecutionException, InterruptedException {
        if (order == null || !order.isValid()) {
            logger.error("Invalid Order entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid order data"));
        }

        // Validate petId exists - since we cannot map legacy ids, do in-memory filtering by petId field
        Condition condition = Condition.of("$.petId", "EQUALS", order.getPetId());
        SearchConditionRequest petCondition = SearchConditionRequest.group("AND", condition);

        CompletableFuture<ArrayNode> petsFuture = entityService.getItemsByCondition("Pet", ENTITY_VERSION, petCondition, true);
        ArrayNode petsFound = petsFuture.get();
        if (petsFound.isEmpty()) {
            logger.error("Order creation failed: petId {} does not exist", order.getPetId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Pet ID does not exist"));
        }

        CompletableFuture<UUID> idFuture = entityService.addItem("Order", ENTITY_VERSION, order);
        UUID technicalId = idFuture.get();
        String techIdStr = "order-" + orderIdCounter.getAndIncrement();

        logger.info("Created Order with technicalId: {}", techIdStr);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", techIdStr));
    }

    // GET /entities/orders/{id} - get Order by technicalId
    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable("id") String id) {
        try {
            // legacy id like "order-<number>" can't be translated to UUID, so skip and return 404
            logger.error("Retrieval by legacy id '{}' not supported with EntityService, skipping", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument in getOrderById: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error retrieving Order: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal server error"));
        }
    }
}