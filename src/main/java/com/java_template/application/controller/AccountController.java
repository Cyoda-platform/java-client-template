package com.java_template.application.controller;

import com.java_template.application.entity.account.version_1.Account;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * AccountController - REST API for account management
 * Handles account creation, retrieval, and updates
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

    /**
     * Create a new account
     * POST /ui/account
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Account>> createAccount(@Valid @RequestBody Account account) {
        try {
            // Check for duplicate business identifier
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

    /**
     * Get account by technical UUID
     * GET /ui/account/{id}
     */
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
                String.format("Failed to retrieve account with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update account
     * PUT /ui/account/{id}?transition=TRANSITION_NAME
     */
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
                String.format("Failed to update account with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get account by business identifier
     * GET /ui/account/business/{accountId}
     */
    @GetMapping("/business/{accountId}")
    public ResponseEntity<EntityWithMetadata<Account>> getAccountByBusinessId(@PathVariable String accountId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Account.ENTITY_NAME).withVersion(Account.ENTITY_VERSION);
            EntityWithMetadata<Account> response = entityService.findByBusinessId(
                    modelSpec, accountId, "accountId", Account.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve account with business ID '%s': %s", accountId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

