package com.example.application.controller;

import com.example.application.entity.customer.version_1.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.QueryCondition;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
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

@Slf4j
@RestController
@RequestMapping("/ui/customers")
@CrossOrigin(origins = "*")
public class CustomerController {

    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public CustomerController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new customer
     * POST /ui/customers
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Customer>> createCustomer(@Valid @RequestBody Customer customer) {
        try {
            // Check for duplicate email
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, customer.getEmail(), "email", Customer.class);

            if (existing != null) {
                log.warn("Customer with email {} already exists", customer.getEmail());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Customer already exists with email: %s", customer.getEmail())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Customer> response = entityService.create(customer);
            log.info("Customer created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            log.error("Failed to create customer", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get customer by technical UUID
     * GET /ui/customers/{id}
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
            log.error("Failed to retrieve customer with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update customer
     * PUT /ui/customers/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Customer>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody Customer customer) {
        try {
            EntityWithMetadata<Customer> response = entityService.update(id, customer, null);
            log.info("Customer updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update customer with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete customer (soft delete)
     * DELETE /ui/customers/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID id) {
        try {
            entityService.delete(id);
            log.info("Customer deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete customer with ID: {}", id, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * List all customers with pagination
     * GET /ui/customers?page=0&size=20&status=VERIFIED
     */
    @GetMapping
    public ResponseEntity<PageResult<EntityWithMetadata<Customer>>> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String kycStatus) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);

            List<QueryCondition> conditions = new ArrayList<>();

            if (status != null && !status.isBlank()) {
                conditions.add(new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status)));
            }

            if (kycStatus != null && !kycStatus.isBlank()) {
                conditions.add(new SimpleCondition()
                    .withJsonPath("$.kyc.kycStatus")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(kycStatus)));
            }

            GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(conditions);

            PageResult<EntityWithMetadata<Customer>> result = entityService.search(
                modelSpec,
                condition,
                Customer.class,
                SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .inMemory(false)
                    .build());

            log.info("Found {} customers", result.data().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to list customers", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to list customers: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Search customers by name or email
     * GET /ui/customers/search?query=john
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Customer>>> searchCustomers(
            @RequestParam String query) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);

            SimpleCondition firstNameCondition = new SimpleCondition()
                .withJsonPath("$.firstName")
                .withOperation(Operation.CONTAINS)
                .withValue(objectMapper.valueToTree(query));

            SimpleCondition lastNameCondition = new SimpleCondition()
                .withJsonPath("$.lastName")
                .withOperation(Operation.CONTAINS)
                .withValue(objectMapper.valueToTree(query));

            SimpleCondition emailCondition = new SimpleCondition()
                .withJsonPath("$.email")
                .withOperation(Operation.CONTAINS)
                .withValue(objectMapper.valueToTree(query));

            GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.OR)
                .withConditions(List.of(firstNameCondition, lastNameCondition, emailCondition));

            PageResult<EntityWithMetadata<Customer>> result = entityService.search(
                modelSpec,
                condition,
                Customer.class,
                SearchAndRetrievalParams.builder()
                    .pageSize(100)
                    .pageNumber(0)
                    .inMemory(true)
                    .build());

            log.info("Found {} customers matching query: {}", result.data().size(), query);
            return ResponseEntity.ok(result.data());
        } catch (Exception e) {
            log.error("Failed to search customers", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search customers: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Transition customer to verified state
     * POST /ui/customers/{id}/verify
     */
    @PostMapping("/{id}/verify")
    public ResponseEntity<EntityWithMetadata<Customer>> verifyCustomer(@PathVariable UUID id) {
        try {
            EntityWithMetadata<Customer> response = entityService.update(id, null, "verify_customer");
            log.info("Customer verified with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to verify customer with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to verify customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Transition customer to suspended state
     * POST /ui/customers/{id}/suspend
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<EntityWithMetadata<Customer>> suspendCustomer(@PathVariable UUID id) {
        try {
            EntityWithMetadata<Customer> response = entityService.update(id, null, "suspend_customer");
            log.info("Customer suspended with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to suspend customer with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to suspend customer: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

