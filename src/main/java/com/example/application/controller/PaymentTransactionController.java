package com.example.application.controller;

import com.example.application.entity.payment_transaction.version_1.PaymentTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * PaymentTransactionController - REST API for payment transaction operations
 * Handles charge creation, authorization, capture, and refunds
 */
@RestController
@RequestMapping("/ui/payment-transaction")
@CrossOrigin(origins = "*")
public class PaymentTransactionController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentTransactionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PaymentTransactionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<PaymentTransaction>> createTransaction(
            @RequestBody PaymentTransaction transaction) {
        try {
            // Check for duplicate transaction ID
            ModelSpec modelSpec = new ModelSpec()
                    .withName(PaymentTransaction.ENTITY_NAME)
                    .withVersion(PaymentTransaction.ENTITY_VERSION);
            
            EntityWithMetadata<PaymentTransaction> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, transaction.getTransactionId(), "transactionId", PaymentTransaction.class);

            if (existing != null) {
                logger.warn("Transaction with ID {} already exists", transaction.getTransactionId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "Transaction already exists with ID: " + transaction.getTransactionId()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<PaymentTransaction> response = entityService.create(transaction);
            logger.info("Payment transaction created: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create transaction", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to create transaction: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<PaymentTransaction>> getTransaction(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(PaymentTransaction.ENTITY_NAME)
                    .withVersion(PaymentTransaction.ENTITY_VERSION);
            
            EntityWithMetadata<PaymentTransaction> response = entityService.getById(
                    id, modelSpec, PaymentTransaction.class);
            
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve transaction", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to retrieve transaction: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<PaymentTransaction>> updateTransaction(
            @PathVariable UUID id,
            @RequestBody PaymentTransaction transaction,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<PaymentTransaction> response = entityService.update(id, transaction, transition);
            logger.info("Payment transaction updated: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update transaction", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to update transaction: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/authorize")
    public ResponseEntity<EntityWithMetadata<PaymentTransaction>> authorizeTransaction(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec()
                    .withName(PaymentTransaction.ENTITY_NAME)
                    .withVersion(PaymentTransaction.ENTITY_VERSION);
            
            EntityWithMetadata<PaymentTransaction> current = entityService.getById(
                    id, modelSpec, PaymentTransaction.class);
            
            current.entity().setStatus("AUTHORIZED");
            EntityWithMetadata<PaymentTransaction> response = entityService.update(id, current.entity(), "AUTHORIZE");
            logger.info("Payment transaction authorized: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to authorize transaction", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to authorize transaction: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Payment transaction deleted: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete transaction", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to delete transaction: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

