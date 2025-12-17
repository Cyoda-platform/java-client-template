package com.java_template.application.controller;

import com.java_template.application.entity.wallet.version_1.Wallet;
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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * WalletController - REST API for Wallet entity management
 * Handles wallet operations, deposits, and withdrawals
 */
@RestController
@RequestMapping("/ui/wallet")
@CrossOrigin(origins = "*")
public class WalletController {

    private static final Logger logger = LoggerFactory.getLogger(WalletController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public WalletController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Wallet>> createWallet(@Valid @RequestBody Wallet wallet) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Wallet.ENTITY_NAME).withVersion(Wallet.ENTITY_VERSION);
            EntityWithMetadata<Wallet> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, wallet.getWalletId(), "walletId", Wallet.class);

            if (existing != null) {
                logger.warn("Wallet with ID {} already exists", wallet.getWalletId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Wallet already exists with ID: %s", wallet.getWalletId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Wallet> response = entityService.create(wallet);
            logger.info("Wallet created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create wallet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Wallet>> getWalletById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Wallet.ENTITY_NAME).withVersion(Wallet.ENTITY_VERSION);
            EntityWithMetadata<Wallet> response = entityService.getById(id, modelSpec, Wallet.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve wallet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<PageResult<EntityWithMetadata<Wallet>>> getWalletsByAccountId(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Wallet.ENTITY_NAME).withVersion(Wallet.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.accountId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(accountId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<Wallet>> result = entityService.search(
                    modelSpec, groupCondition, Wallet.class, params);

            logger.info("Found {} wallets for account: {}", result.data().size(), accountId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve wallets: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Wallet>> updateWallet(
            @PathVariable UUID id,
            @Valid @RequestBody Wallet wallet,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Wallet> response = entityService.update(id, wallet, transition);
            logger.info("Wallet updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update wallet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<EntityWithMetadata<Wallet>> initiateDeposit(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Wallet.ENTITY_NAME).withVersion(Wallet.ENTITY_VERSION);
            EntityWithMetadata<Wallet> current = entityService.getById(id, modelSpec, Wallet.class);
            
            EntityWithMetadata<Wallet> response = entityService.update(id, current.entity(), "initiate_deposit");
            logger.info("Deposit initiated for wallet: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to initiate deposit: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<EntityWithMetadata<Wallet>> initiateWithdrawal(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Wallet.ENTITY_NAME).withVersion(Wallet.ENTITY_VERSION);
            EntityWithMetadata<Wallet> current = entityService.getById(id, modelSpec, Wallet.class);
            
            EntityWithMetadata<Wallet> response = entityService.update(id, current.entity(), "initiate_withdrawal");
            logger.info("Withdrawal initiated for wallet: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to initiate withdrawal: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWallet(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Wallet deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete wallet: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

