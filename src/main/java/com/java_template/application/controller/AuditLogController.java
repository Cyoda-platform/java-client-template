package com.java_template.application.controller;

import com.java_template.application.entity.audit_log.version_1.AuditLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.dto.PageResult;
import com.java_template.common.repository.SearchAndRetrievalParams;
import com.java_template.common.service.EntityService;
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
 * REST Controller for AuditLog entity
 * Provides audit trail and compliance reporting endpoints
 */
@RestController
@RequestMapping("/ui/audit-logs")
@CrossOrigin(origins = "*")
public class AuditLogController {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AuditLogController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<AuditLog>> createLog(@RequestBody AuditLog log) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AuditLog.ENTITY_NAME).withVersion(AuditLog.ENTITY_VERSION);
            EntityWithMetadata<AuditLog> response = entityService.create(log);
            logger.info("Audit log created with ID: {}", response.metadata().getId());

            URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.metadata().getId())
                .toUri();

            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to create audit log: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<AuditLog>> getLogById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AuditLog.ENTITY_NAME).withVersion(AuditLog.ENTITY_VERSION);
            EntityWithMetadata<AuditLog> response = entityService.getById(id, modelSpec, AuditLog.class);
            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to retrieve audit log: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-entity")
    public ResponseEntity<PageResult<EntityWithMetadata<AuditLog>>> searchByEntity(
            @RequestParam String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AuditLog.ENTITY_NAME).withVersion(AuditLog.ENTITY_VERSION);

            SimpleCondition entityCondition = new SimpleCondition()
                    .withJsonPath("$.entityId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(entityId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(entityCondition));

            PageResult<EntityWithMetadata<AuditLog>> result = entityService.search(
                    modelSpec,
                    condition,
                    AuditLog.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(size)
                            .pageNumber(page)
                            .build());

            logger.info("Found {} audit logs for entity '{}'", result.data().size(), entityId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search audit logs: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }

    @GetMapping("/search/by-user")
    public ResponseEntity<List<EntityWithMetadata<AuditLog>>> searchByUser(@RequestParam String userId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AuditLog.ENTITY_NAME).withVersion(AuditLog.ENTITY_VERSION);

            SimpleCondition userCondition = new SimpleCondition()
                    .withJsonPath("$.userId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(userId));

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(userCondition));

            PageResult<EntityWithMetadata<AuditLog>> result = entityService.search(
                    modelSpec,
                    condition,
                    AuditLog.class,
                    SearchAndRetrievalParams.builder()
                            .pageSize(1000)
                            .pageNumber(0)
                            .inMemory(true)
                            .build());

            logger.info("Found {} audit logs for user '{}'", result.data().size(), userId);
            return ResponseEntity.ok(result.data());
        } catch (Exception e) {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format("Failed to search audit logs: %s", e.getMessage())
            );
            return ResponseEntity.of(problemDetail).build();
        }
    }
}

