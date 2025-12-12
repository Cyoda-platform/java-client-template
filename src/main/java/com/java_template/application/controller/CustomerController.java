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
 * Customer Controller for Customer Management System
 * 
 * Provides REST endpoints for customer lifecycle management including:
 * - CRUD operations
 * - Workflow transitions (onboarding, verification, suspension, termination)
 * - Search and filtering
 * - Audit trail access
 * 
 * Maps to /ui/customers/** endpoints as specified in functional requirements.
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
     * Create a new customer
     * POST /ui/customers
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

            // Build Location header for the created resource
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
     * GET /ui/customers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Customer>> getCustomerById(
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
                String.format("Failed to retrieve customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get customer by business identifier
     * GET /ui/customers/business/{customerId}
     */
    @GetMapping("/business/{customerId}")
    public ResponseEntity<EntityWithMetadata<Customer>> getCustomerByBusinessId(
            @PathVariable String customerId,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
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
     * PUT /ui/customers/{id}?transition=TRANSITION_NAME
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
     * List all customers with pagination and optional filtering
     * GET /ui/customers?page=0&size=20&state=active&email=example.com
     */
    @GetMapping
    public ResponseEntity<Page<EntityWithMetadata<Customer>>> listCustomers(
            Pageable pageable,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;

            List<QueryCondition> conditions = new ArrayList<>();

            // Add entity field filters
            if (email != null && !email.trim().isEmpty()) {
                SimpleCondition emailCondition = new SimpleCondition()
                        .withJsonPath("$.email")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(email));
                conditions.add(emailCondition);
            }

            if (name != null && !name.trim().isEmpty()) {
                SimpleCondition nameCondition = new SimpleCondition()
                        .withJsonPath("$.name")
                        .withOperation(Operation.CONTAINS)
                        .withValue(objectMapper.valueToTree(name));
                conditions.add(nameCondition);
            }

            if (conditions.isEmpty() && (state == null || state.trim().isEmpty())) {
                // Use paginated findAll when no filters
                return ResponseEntity.ok(entityService.findAll(modelSpec, pageable, Customer.class, pointInTimeDate));
            } else {
                // For filtered results, get all matching results then manually paginate
                List<EntityWithMetadata<Customer>> customers;
                if (conditions.isEmpty()) {
                    customers = entityService.findAll(modelSpec, Customer.class, pointInTimeDate);
                } else {
                    GroupCondition groupCondition = new GroupCondition()
                            .withOperator(GroupCondition.Operator.AND)
                            .withConditions(conditions);
                    customers = entityService.search(modelSpec, groupCondition, Customer.class, pointInTimeDate);
                }

                // Filter by state if provided (state is in metadata, not entity)
                if (state != null && !state.trim().isEmpty()) {
                    customers = customers.stream()
                            .filter(customer -> state.equals(customer.metadata().getState()))
                            .toList();
                }

                // Manually paginate the filtered results
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
     * Get customer change history metadata
     * GET /ui/customers/{id}/changes
     */
    @GetMapping("/{id}/changes")
    public ResponseEntity<List<EntityChangeMeta>> getCustomerChangesMetadata(
            @PathVariable UUID id,
            @RequestParam(required = false) OffsetDateTime pointInTime) {
        try {
            Date pointInTimeDate = pointInTime != null
                ? Date.from(pointInTime.toInstant())
                : null;
            List<EntityChangeMeta> changes =
                    entityService.getEntityChangesMetadata(id, pointInTimeDate);
            return ResponseEntity.ok(changes);
        } catch (Exception e) {
            // Check if it's a NOT_FOUND error (entity doesn't exist)
            if (CyodaExceptionUtil.isNotFound(e)) {
                return ResponseEntity.notFound().build();
            }
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve change history for customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Start onboarding process
     * POST /ui/customers/{id}/onboarding
     */
    @PostMapping("/{id}/onboarding")
    public ResponseEntity<EntityWithMetadata<Customer>> startOnboarding(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "start_onboarding");
            logger.info("Onboarding started for customer with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to start onboarding for customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Request verification
     * POST /ui/customers/{id}/verification
     */
    @PostMapping("/{id}/verification")
    public ResponseEntity<EntityWithMetadata<Customer>> requestVerification(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "request_verification");
            logger.info("Verification requested for customer with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to request verification for customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Skip verification (manual approval)
     * POST /ui/customers/{id}/skip-verification
     */
    @PostMapping("/{id}/skip-verification")
    public ResponseEntity<EntityWithMetadata<Customer>> skipVerification(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "skip_verification");
            logger.info("Verification skipped for customer with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to skip verification for customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Activate customer
     * POST /ui/customers/{id}/activate
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<EntityWithMetadata<Customer>> activateCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "activate");
            logger.info("Customer activated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to activate customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Suspend customer
     * POST /ui/customers/{id}/suspend
     */
    @PostMapping("/{id}/suspend")
    public ResponseEntity<EntityWithMetadata<Customer>> suspendCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "suspend");
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

    /**
     * Reinstate customer
     * POST /ui/customers/{id}/reinstate
     */
    @PostMapping("/{id}/reinstate")
    public ResponseEntity<EntityWithMetadata<Customer>> reinstateCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "reinstate");
            logger.info("Customer reinstated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to reinstate customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Request deactivation
     * POST /ui/customers/{id}/deactivation
     */
    @PostMapping("/{id}/deactivation")
    public ResponseEntity<EntityWithMetadata<Customer>> requestDeactivation(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "request_deactivation");
            logger.info("Deactivation requested for customer with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to request deactivation for customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Terminate customer
     * POST /ui/customers/{id}/terminate
     */
    @PostMapping("/{id}/terminate")
    public ResponseEntity<EntityWithMetadata<Customer>> terminateCustomer(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "terminate");
            logger.info("Customer terminated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to terminate customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Cancel termination
     * POST /ui/customers/{id}/cancel-termination
     */
    @PostMapping("/{id}/cancel-termination")
    public ResponseEntity<EntityWithMetadata<Customer>> cancelTermination(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Customer.ENTITY_NAME).withVersion(Customer.ENTITY_VERSION);
            EntityWithMetadata<Customer> current = entityService.getById(id, modelSpec, Customer.class);

            EntityWithMetadata<Customer> response = entityService.update(id, current.entity(), "cancel_termination");
            logger.info("Termination cancelled for customer with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to cancel termination for customer with ID '%s': %s", id, e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
