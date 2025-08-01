package com.java_template.application.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;

    // POST /entities/productuploadjob
    @PostMapping("/productuploadjob")
    public ResponseEntity<String> createProductUploadJob(@RequestBody ProductUploadJob entity) {
        try {
            if (entity == null || !entity.isValid()) {
                log.error("Invalid ProductUploadJob entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(ProductUploadJob.ENTITY_NAME, ENTITY_VERSION, entity);
            UUID technicalId = idFuture.join();

            processProductUploadJob(technicalId.toString(), entity);

            log.info("Processed ProductUploadJob with id {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(technicalId.toString());
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createProductUploadJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("Error processing ProductUploadJob", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // GET /entities/productuploadjob/{id}
    @GetMapping("/productuploadjob/{id}")
    public ResponseEntity<ProductUploadJob> getProductUploadJob(@PathVariable("id") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ProductUploadJob.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                log.error("ProductUploadJob not found with id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            ProductUploadJob entity = JsonUtil.fromObjectNode(node, ProductUploadJob.class);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in getProductUploadJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("Error retrieving ProductUploadJob with id " + technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // POST /entities/customerprofileupdate
    @PostMapping("/customerprofileupdate")
    public ResponseEntity<String> createCustomerProfileUpdate(@RequestBody CustomerProfileUpdate entity) {
        try {
            if (entity == null || !entity.isValid()) {
                log.error("Invalid CustomerProfileUpdate entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(CustomerProfileUpdate.ENTITY_NAME, ENTITY_VERSION, entity);
            UUID technicalId = idFuture.join();

            processCustomerProfileUpdate(technicalId.toString(), entity);

            log.info("Processed CustomerProfileUpdate with id {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(technicalId.toString());
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createCustomerProfileUpdate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("Error processing CustomerProfileUpdate", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // GET /entities/customerprofileupdate/{id}
    @GetMapping("/customerprofileupdate/{id}")
    public ResponseEntity<CustomerProfileUpdate> getCustomerProfileUpdate(@PathVariable("id") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(CustomerProfileUpdate.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                log.error("CustomerProfileUpdate not found with id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            CustomerProfileUpdate entity = JsonUtil.fromObjectNode(node, CustomerProfileUpdate.class);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in getCustomerProfileUpdate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("Error retrieving CustomerProfileUpdate with id " + technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // POST /entities/order
    @PostMapping("/order")
    public ResponseEntity<String> createOrder(@RequestBody Order entity) {
        try {
            if (entity == null || !entity.isValid()) {
                log.error("Invalid Order entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }
            CompletableFuture<UUID> idFuture = entityService.addItem(Order.ENTITY_NAME, ENTITY_VERSION, entity);
            UUID technicalId = idFuture.join();

            processOrder(technicalId.toString(), entity);

            log.info("Processed Order with id {}", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(technicalId.toString());
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument in createOrder", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("Error processing Order", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // GET /entities/order/{id}
    @GetMapping("/order/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable("id") String technicalId) {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Order.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                log.error("Order not found with id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            Order entity = JsonUtil.fromObjectNode(node, Order.class);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in getOrder", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("Error retrieving Order with id " + technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // Business logic implementations

    private void processProductUploadJob(String technicalId, ProductUploadJob entity) {
        log.info("Starting processing ProductUploadJob: {}", technicalId);
        if (entity.getCsvData() == null || entity.getCsvData().isBlank()) {
            log.error("CSV data is blank for ProductUploadJob: {}", technicalId);
            entity.setStatus("FAILED");
            return;
        }
        entity.setStatus("PROCESSING");
        log.info("Parsing CSV for ProductUploadJob: {}", technicalId);
        // Simulate parsing CSV and creating Product entities
        // TODO: Implement product creation based on CSV parsing
        entity.setStatus("COMPLETED");
        log.info("Completed processing ProductUploadJob: {}", technicalId);
    }

    private void processCustomerProfileUpdate(String technicalId, CustomerProfileUpdate entity) {
        log.info("Processing CustomerProfileUpdate: {}", technicalId);
        if (entity.getUpdatedFields() == null || entity.getUpdatedFields().isEmpty()) {
            log.error("Updated fields are empty for CustomerProfileUpdate: {}", technicalId);
            return;
        }
        // Apply changes as immutable event (logging here)
        log.info("Applied profile updates for customerId: {}", entity.getCustomerId());
        // Confirmation logic can be added here if needed
    }

    private void processOrder(String technicalId, Order entity) {
        log.info("Processing Order: {}", technicalId);
        boolean stockAvailable = true; // simplified assumption
        if (!stockAvailable) {
            log.error("Stock not available for Order: {}", technicalId);
            entity.setStatus("FAILED");
            return;
        }
        // Deduct stock (not implemented here)
        // Calculate totals assumed valid in isValid
        entity.setStatus("CONFIRMED");
        log.info("Order confirmed: {}", technicalId);
        // Notify customer, update inventory, etc.
    }

    // Utility for JSON conversion (assuming Jackson ObjectMapper available)
    // This is a placeholder; adapt if you have a specific utility class
    private static class JsonUtil {
        private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        static <T> T fromObjectNode(ObjectNode node, Class<T> clazz) {
            try {
                return mapper.treeToValue(node, clazz);
            } catch (Exception e) {
                throw new RuntimeException("Error converting ObjectNode to " + clazz.getSimpleName(), e);
            }
        }
    }
}