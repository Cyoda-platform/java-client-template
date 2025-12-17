package com.java_template.application.controller;

import com.java_template.application.entity.account.version_1.Account;
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
 * AccountController - REST API for Account entity management
 * Handles trading account operations and lifecycle
 */
@RestController
@RequestMapping("/ui/account")
@CrossOrigin(origins = "*")
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AccountController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Account>> createAccount(@Valid @RequestBody Account account) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Account.ENTITY_NAME).withVersion(Account.ENTITY_VERSION);
            EntityWithMetadata<Account> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, account.getAccountId(), "accountId", Account.class);

            if (existing != null) {
                logger.warn("Account with ID {} already exists", account.getAccountId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Account already exists with ID: %s", account.getAccountId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Account> response = entityService.create(account);
            logger.info("Account created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create account: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Account>> getAccountById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Account.ENTITY_NAME).withVersion(Account.ENTITY_VERSION);
            EntityWithMetadata<Account> response = entityService.getById(id, modelSpec, Account.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve account: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<PageResult<EntityWithMetadata<Account>>> getAccountsByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Account.ENTITY_NAME).withVersion(Account.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.userId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(userId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<Account>> result = entityService.search(
                    modelSpec, groupCondition, Account.class, params);

            logger.info("Found {} accounts for user: {}", result.data().size(), userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve accounts: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Account>> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody Account account,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Account> response = entityService.update(id, account, transition);
            logger.info("Account updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update account: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Account deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete account: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

