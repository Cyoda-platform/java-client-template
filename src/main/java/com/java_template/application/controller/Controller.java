package com.java_template.application.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.CustomerProfileUpdate;
import com.java_template.application.entity.Order;
import com.java_template.application.entity.ProductUploadJob;
import com.java_template.common.service.EntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@RestController
@RequestMapping(path = "/entities")
@RequiredArgsConstructor
@Slf4j
public class Controller {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

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

            // processProductUploadJob removed

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
    public ResponseEntity<ProductUploadJob> getProductUploadJob(@PathVariable("id") String technicalId) throws JsonProcessingException {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(ProductUploadJob.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                log.error("ProductUploadJob not found with id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            ProductUploadJob entity = objectMapper.treeToValue(node, ProductUploadJob.class);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in getProductUploadJob", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON in getProductUploadJob", e);
            throw e;
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

            // processCustomerProfileUpdate removed

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
    public ResponseEntity<CustomerProfileUpdate> getCustomerProfileUpdate(@PathVariable("id") String technicalId) throws JsonProcessingException {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(CustomerProfileUpdate.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                log.error("CustomerProfileUpdate not found with id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            CustomerProfileUpdate entity = objectMapper.treeToValue(node, CustomerProfileUpdate.class);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in getCustomerProfileUpdate", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON in getCustomerProfileUpdate", e);
            throw e;
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

            // processOrder removed

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
    public ResponseEntity<Order> getOrder(@PathVariable("id") String technicalId) throws JsonProcessingException {
        try {
            UUID id = UUID.fromString(technicalId);
            CompletableFuture<ObjectNode> itemFuture = entityService.getItem(Order.ENTITY_NAME, ENTITY_VERSION, id);
            ObjectNode node = itemFuture.join();
            if (node == null || node.isEmpty()) {
                log.error("Order not found with id {}", technicalId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
            Order entity = objectMapper.treeToValue(node, Order.class);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in getOrder", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON in getOrder", e);
            throw e;
        } catch (Exception e) {
            log.error("Error retrieving Order with id " + technicalId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}