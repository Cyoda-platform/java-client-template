package com.example.application.controller;

import com.example.application.entity.customer.version_1.Customer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
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
 * Customer REST API Controller
 * Implements CRUD operations with pagination, filtering, and soft delete
 * Follows REST best practices with proper HTTP status codes and error handling
 */
@RestController
@RequestMapping("/ui/customers")
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
     * Create a new customer (POST /ui/customers)
     * Returns 201 Created with Location header
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Customer>> createCustomer(@Valid @RequestBody Customer customer) {
        try {
            // Check for duplicate email
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, customer.getEmail(), "email", Customer.class);

            if (existing != null) {
                logger.warn("Customer with email {} already exists", customer.getEmail());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    "Customer already exists with this email: " + customer.getEmail()
                );
                return ResponseEntity.of(problemDetail).build();
            }

            // Set initial audit fields
            OffsetDateTime now = OffsetDateTime.now();
            customer.setCreatedAt(now);
            customer.setUpdatedAt(now);
            customer.setDeleted(false);

            EntityWithMetadata<Customer> response = entityService.create(customer);
            logger.info("Customer created: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to create customer: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get customer by UUID (GET /ui/customers/{id})
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Customer>> getCustomer(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Customer> response = entityService.getById(id, modelSpec, Customer.class, pointInTimeDate);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to retrieve customer: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get customer by email (GET /ui/customers/by-email/{email})
     */
    @GetMapping("/by-email/{email}")
    public ResponseEntity<EntityWithMetadata<Customer>> getCustomerByEmail(
            @PathVariable String email,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            EntityWithMetadata<Customer> response = entityService.findByBusinessId(
                    modelSpec, email, "email", Customer.class, pointInTimeDate);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to retrieve customer: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * List customers with pagination and optional filtering (GET /ui/customers)
     * Excludes soft-deleted customers by default
     */
    @GetMapping
    public ResponseEntity<PageResult<EntityWithMetadata<Customer>>> listCustomers(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID searchId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            // Always filter out deleted customers
            conditions.add(new SimpleCondition()
                    .withJsonPath("$.deleted")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(false)));

            if (email != null && !email.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.email")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(email)));
            }

            if (firstName != null && !firstName.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.firstName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(firstName)));
            }

            if (lastName != null && !lastName.trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.lastName")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(lastName)));
            }

            SearchAndRetrievalParams paginationParams =
                    SearchAndRetrievalParams.builder()
                            .pageSize(size)
                            .pageNumber(page)
                            .pointInTime(pointInTimeDate)
                            .searchId(searchId)
                            .build();

            PageResult<EntityWithMetadata<Customer>> pageResult;

            if (conditions.size() == 1) {
                // Only the deleted filter - use findAll
                pageResult = entityService.findAll(modelSpec, Customer.class, paginationParams);
            } else {
                // Multiple conditions - use search
                GroupCondition condition = new GroupCondition()
                        .withOperator(GroupCondition.Operator.AND)
                        .withConditions(conditions);

                pageResult = entityService.search(modelSpec, condition, Customer.class, paginationParams);
            }

            logger.info("Found {} customers (page {} of {})", pageResult.totalElements(), pageResult.pageNumber(), pageResult.totalPages());
            return ResponseEntity.ok(pageResult);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to list customers: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update customer (PUT /ui/customers/{id})
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Customer>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody Customer customer) {
        try {
            // Fetch current customer to preserve non-updatable fields
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            // Update audit fields
            customer.setUpdatedAt(OffsetDateTime.now());
            customer.setCreatedAt(current.entity().getCreatedAt());
            customer.setCreatedBy(current.entity().getCreatedBy());

            EntityWithMetadata<Customer> response = entityService.update(id, customer);
            logger.info("Customer updated: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to update customer: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Partial update customer (PATCH /ui/customers/{id})
     */
    @PatchMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Customer>> partialUpdateCustomer(
            @PathVariable UUID id,
            @RequestBody Customer updates) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            Customer existing = current.entity();

            // Merge updates (only non-null fields)
            if (updates.getFirstName() != null) existing.setFirstName(updates.getFirstName());
            if (updates.getLastName() != null) existing.setLastName(updates.getLastName());
            if (updates.getEmail() != null) existing.setEmail(updates.getEmail());
            if (updates.getPhone() != null) existing.setPhone(updates.getPhone());
            if (updates.getAddress() != null) existing.setAddress(updates.getAddress());

            existing.setUpdatedAt(OffsetDateTime.now());

            EntityWithMetadata<Customer> response = entityService.update(id, existing);
            logger.info("Customer partially updated: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to update customer: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Soft delete customer (DELETE /ui/customers/{id})
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            if (current == null) {
                return ResponseEntity.notFound().build();
            }

            // Soft delete - mark as deleted
            Customer customer = current.entity();
            customer.setDeleted(true);
            customer.setUpdatedAt(OffsetDateTime.now());

            entityService.update(id, customer);
            logger.info("Customer soft-deleted: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to delete customer: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get entity change history (GET /ui/customers/{id}/changes)
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getCustomerChanges(@PathVariable UUID id) {
        try {
            List<EntityChangeMeta> changes = entityService.getEntityChangesMetadata(id, null);
            return ResponseEntity.ok(changes);
        } catch (Exception e) {
            if (CyodaExceptionUtil.isNotFound(e)) {
                return ResponseEntity.notFound().build();
            }
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Failed to retrieve change history: " + e.getMessage()
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

