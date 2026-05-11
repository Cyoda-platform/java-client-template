package com.java_template.application.controller;

import com.java_template.application.entity.customer.version_1.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.net.URI;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Customer entity CRUD operations.
 * Provides REST endpoints following the /ui/customer pattern with EntityService integration.
 */
@RestController
@RequestMapping("/ui/customer")
@CrossOrigin(origins = "*")
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CustomerController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new customer
     * POST /ui/customer
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Customer>> createCustomer(@Valid @RequestBody Customer customer) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            
            // Check for duplicate business identifier
            EntityWithMetadata<Customer> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, customer.getCustomerId(), "customerId", Customer.class);

            if (existing != null) {
                logger.warn("Customer with ID {} already exists", customer.getCustomerId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Customer already exists with ID: %s", customer.getCustomerId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Customer> response = entityService.create(customer);
            logger.info("Customer created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get customer by technical UUID
     * GET /ui/customer/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Customer>> getCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> response = entityService.getById(id, modelSpec, Customer.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update customer
     * PUT /ui/customer/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Customer>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody Customer customer) {
        try {
            EntityWithMetadata<Customer> response = entityService.update(id, customer, null);
            logger.info("Customer updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete customer
     * DELETE /ui/customer/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Customer deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to delete customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

