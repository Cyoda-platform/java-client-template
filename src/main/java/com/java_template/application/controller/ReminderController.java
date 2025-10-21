package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.reminder.version_1.Reminder;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import jakarta.validation.Valid;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ABOUTME: REST controller for Reminder entity providing CRUD operations
 * and search functionality for the CRM system.
 */
@RestController
@RequestMapping("/ui/reminder")
@CrossOrigin(origins = "*")
public class ReminderController {

    private static final Logger logger = LoggerFactory.getLogger(ReminderController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ReminderController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new reminder
     * POST /ui/reminder
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<Reminder>> createReminder(@Valid @RequestBody Reminder reminder) {
        try {
            // Check for duplicate business identifier
            ModelSpec modelSpec = new ModelSpec().withName(Reminder.ENTITY_NAME).withVersion(Reminder.ENTITY_VERSION);
            EntityWithMetadata<Reminder> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, reminder.getReminderId(), "reminderId", Reminder.class);

            if (existing != null) {
                logger.warn("Reminder with business ID {} already exists", reminder.getReminderId());
                ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT,
                    String.format("Reminder already exists with ID: %s", reminder.getReminderId())
                );
                return ResponseEntity.of(problemDetail).build();
            }

            EntityWithMetadata<Reminder> response = entityService.create(reminder);
            logger.info("Reminder created with ID: {}", response.metadata().getId());

            // Build Location header for the created resource
            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create reminder", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create reminder: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get reminder by technical ID
     * GET /ui/reminder/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Reminder>> getReminderById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Reminder.ENTITY_NAME).withVersion(Reminder.ENTITY_VERSION);
            EntityWithMetadata<Reminder> reminder = entityService.getById(id, modelSpec, Reminder.class);
            return ResponseEntity.ok(reminder);
        } catch (Exception e) {
            logger.error("Failed to get reminder by ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Reminder not found with ID: %s", id)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get reminder by business ID
     * GET /ui/reminder/business/{businessId}
     */
    @GetMapping("/business/{businessId}")
    public ResponseEntity<EntityWithMetadata<Reminder>> getReminderByBusinessId(@PathVariable String businessId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Reminder.ENTITY_NAME).withVersion(Reminder.ENTITY_VERSION);
            EntityWithMetadata<Reminder> reminder = entityService.findByBusinessId(
                    modelSpec, businessId, "reminderId", Reminder.class);
            return ResponseEntity.ok(reminder);
        } catch (Exception e) {
            logger.error("Failed to get reminder by business ID: {}", businessId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Reminder not found with business ID: %s", businessId)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Update reminder
     * PUT /ui/reminder/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Reminder>> updateReminder(
            @PathVariable UUID id, 
            @Valid @RequestBody Reminder reminder,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Reminder> response = entityService.update(id, reminder, transition);
            logger.info("Reminder updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update reminder with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to update reminder: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Delete reminder
     * DELETE /ui/reminder/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReminder(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            logger.info("Reminder deleted with ID: {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete reminder with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                String.format("Reminder not found with ID: %s", id)
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Search reminders
     * GET /ui/reminder/search
     */
    @GetMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<Reminder>>> searchReminders(
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Boolean completed) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Reminder.ENTITY_NAME).withVersion(Reminder.ENTITY_VERSION);
            List<QueryCondition> conditions = new ArrayList<>();

            if (companyId != null && !companyId.trim().isEmpty()) {
                SimpleCondition companyCondition = new SimpleCondition()
                        .withJsonPath("$.companyId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(companyId));
                conditions.add(companyCondition);
            }

            if (priority != null && !priority.trim().isEmpty()) {
                SimpleCondition priorityCondition = new SimpleCondition()
                        .withJsonPath("$.priority")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(priority));
                conditions.add(priorityCondition);
            }

            if (completed != null) {
                SimpleCondition completedCondition = new SimpleCondition()
                        .withJsonPath("$.completed")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(completed));
                conditions.add(completedCondition);
            }

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(conditions);

            List<EntityWithMetadata<Reminder>> reminders = conditions.isEmpty() 
                ? entityService.findAll(modelSpec, Reminder.class)
                : entityService.search(modelSpec, groupCondition, Reminder.class);

            return ResponseEntity.ok(reminders);
        } catch (Exception e) {
            logger.error("Failed to search reminders", e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                String.format("Failed to search reminders: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Get reminders by company ID
     * GET /ui/reminder/company/{companyId}
     */
    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<EntityWithMetadata<Reminder>>> getRemindersByCompany(@PathVariable String companyId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Reminder.ENTITY_NAME).withVersion(Reminder.ENTITY_VERSION);
            
            SimpleCondition companyCondition = new SimpleCondition()
                    .withJsonPath("$.companyId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(companyId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(companyCondition));

            List<EntityWithMetadata<Reminder>> reminders = entityService.search(modelSpec, groupCondition, Reminder.class);
            return ResponseEntity.ok(reminders);
        } catch (Exception e) {
            logger.error("Failed to get reminders for company: {}", companyId, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                String.format("Failed to get reminders for company: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Complete reminder (transition to completed state)
     * POST /ui/reminder/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<EntityWithMetadata<Reminder>> completeReminder(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Reminder.ENTITY_NAME).withVersion(Reminder.ENTITY_VERSION);
            EntityWithMetadata<Reminder> reminder = entityService.getById(id, modelSpec, Reminder.class);
            EntityWithMetadata<Reminder> response = entityService.update(id, reminder.entity(), "complete_reminder");
            logger.info("Reminder completed with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to complete reminder with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to complete reminder: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    /**
     * Cancel reminder (transition to cancelled state)
     * POST /ui/reminder/{id}/cancel
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<EntityWithMetadata<Reminder>> cancelReminder(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(Reminder.ENTITY_NAME).withVersion(Reminder.ENTITY_VERSION);
            EntityWithMetadata<Reminder> reminder = entityService.getById(id, modelSpec, Reminder.class);
            EntityWithMetadata<Reminder> response = entityService.update(id, reminder.entity(), "cancel_reminder");
            logger.info("Reminder cancelled with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to cancel reminder with ID: {}", id, e);
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to cancel reminder: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}
