package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.customer.version_1.Customer;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.CyodaExceptionUtil;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.EntityChangeMeta;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Customer entity providing CRUD operations, search functionality,
 * and workflow transition endpoints following the thin proxy pattern.
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
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, customer.getCustomerId(), "customerId", Customer.class);

            if (existing != null) {
                logger.warn("Customer with business ID {} already exists", customer.getCustomerId());
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
    public ResponseEntity<EntityWithMetadata<Customer>> getCustomerById(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<Customer> response = entityService.getById(id, modelSpec, Customer.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get customer by business identifier
     * GET /ui/customer/business/{customerId}
     */
    @GetMapping("/business/{customerId}")
    public ResponseEntity<EntityWithMetadata<Customer>> getCustomerByBusinessId(
            @PathVariable String customerId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;
            EntityWithMetadata<Customer> response = entityService.findByBusinessId(
                    modelSpec, customerId, "customerId", Customer.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve customer with business ID '%s': %s", customerId, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update customer with optional workflow transition
     * PUT /ui/customer/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Customer>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody Customer customer,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Customer> response = entityService.update(id, customer, transition);
            logger.info("Customer updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete customer by technical UUID
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
                String.format("Failed to delete customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List all customers with pagination and optional filtering
     * GET /ui/customer
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Customer>>> listCustomers(
            Pageable pageable,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null ? Date.from(pointInTime.toInstant()) : null;

            List<QueryCondition> conditions = new ArrayList<>();

            if (city != null && !city.trim().isEmpty()) {
                SimpleCondition cityCondition = new SimpleCondition()
                        .withJsonPath("$.address.city")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(city));
                conditions.add(cityCondition);
            }

            if (conditions.isEmpty() && (status == null || status.trim().isEmpty())) {
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Customer.class, pointInTimeDate));
            } else {
                List<EntityWithMetadata<Customer>> customers;
                if (conditions.isEmpty()) {
                    customers = entityService.findAll(modelSpec, Customer.class, pointInTimeDate);
                } else {
                    GroupCondition groupCondition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    customers = entityService.search(modelSpec, groupCondition, Customer.class, pointInTimeDate);
                }

                if (status != null && !status.trim().isEmpty()) {
                    customers = customers.stream()
                            .filter(customer -> status.equals(customer.metadata().getState()))
                            .toList();
                }

                int start = (int) pageable.getOffset();
                int end = Math.min(start + pageable.getPageSize(), customers.size());
                List<EntityWithMetadata<Customer>> pageContent = start < customers.size()
                    ? customers.subList(start, end)
                    : new ArrayList<>();

                Page<EntityWithMetadata<Customer>> page = new PageImpl<>(pageContent, pageable, customers.size());
                return ResponseEntity.ok(page);
            }
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list customers: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Deactivate customer
     * POST /ui/customer/{id}/deactivate
     */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<EntityWithMetadata<Customer>> deactivateCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "deactivate_customer");
            logger.info("Customer deactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to deactivate customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Reactivate customer
     * POST /ui/customer/{id}/reactivate
     */
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<EntityWithMetadata<Customer>> reactivateCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "reactivate_customer");
            logger.info("Customer reactivated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to reactivate customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Suspend customer
     * POST /ui/customer/{id}/suspend
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<EntityWithMetadata<Customer>> suspendCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "suspend_customer");
            logger.info("Customer suspended with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to suspend customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}
