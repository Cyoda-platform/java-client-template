package com.java_template.application.controller;

import com.java_template.application.entity.transaction.version_1.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * TransactionController - REST API for Transaction entity management
 */
@RestController
@RequestMapping("/ui/transaction")
@CrossOrigin(origins = "*")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public TransactionController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Transaction>> createTransaction(@Valid @RequestBody Transaction transaction) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Transaction.ENTITY_NAME).withVersion(Transaction.ENTITY_VERSION);
            EntityWithMetadata<Transaction> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, transaction.getTransactionId(), "transactionId", Transaction.class);

            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            EntityWithMetadata<Transaction> response = entityService.create(transaction);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.metadata().getId()).toUri();
            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create transaction", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Transaction>> getTransactionById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Transaction.ENTITY_NAME).withVersion(Transaction.ENTITY_VERSION);
            EntityWithMetadata<Transaction> response = entityService.getById(id, modelSpec, Transaction.class);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve transaction", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<PageResult<EntityWithMetadata<Transaction>>> getTransactionsByWalletId(
            @PathVariable String walletId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Transaction.ENTITY_NAME).withVersion(Transaction.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.walletId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(walletId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<Transaction>> result = entityService.search(
                    modelSpec, groupCondition, Transaction.class, params);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to retrieve transactions", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Transaction>> updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody Transaction transaction,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Transaction> response = entityService.update(id, transaction, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update transaction", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete transaction", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

