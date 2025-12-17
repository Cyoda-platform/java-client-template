package com.java_template.application.controller;

import com.java_template.application.entity.compliancealert.version_1.ComplianceAlert;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * ComplianceAlertController - REST API for Compliance Alert entity management
 */
@RestController
@RequestMapping("/ui/compliance-alert")
@CrossOrigin(origins = "*")
public class ComplianceAlertController {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceAlertController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ComplianceAlertController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<ComplianceAlert>> createAlert(@Valid @RequestBody ComplianceAlert alert) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ComplianceAlert.ENTITY_NAME).withVersion(ComplianceAlert.ENTITY_VERSION);
            EntityWithMetadata<ComplianceAlert> existing = entityService.findByBusinessIdOrNull(
                    modelSpec, alert.getAlertId(), "alertId", ComplianceAlert.class);

            if (existing != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            EntityWithMetadata<ComplianceAlert> response = entityService.create(alert);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.metadata().getId()).toUri();
            return ResponseEntity.created(location).body(response);
        } catch (Exception e) {
            logger.error("Failed to create compliance alert", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ComplianceAlert>> getAlertById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ComplianceAlert.ENTITY_NAME).withVersion(ComplianceAlert.ENTITY_VERSION);
            EntityWithMetadata<ComplianceAlert> response = entityService.getById(id, modelSpec, ComplianceAlert.class);
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Failed to retrieve compliance alert", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<PageResult<EntityWithMetadata<ComplianceAlert>>> getAlertsByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ComplianceAlert.ENTITY_NAME).withVersion(ComplianceAlert.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.status")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(status));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<ComplianceAlert>> result = entityService.search(
                    modelSpec, groupCondition, ComplianceAlert.class, params);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to retrieve compliance alerts", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/severity/{severity}")
    public ResponseEntity<PageResult<EntityWithMetadata<ComplianceAlert>>> getAlertsBySeverity(
            @PathVariable String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(ComplianceAlert.ENTITY_NAME).withVersion(ComplianceAlert.ENTITY_VERSION);
            
            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.severity")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(severity));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            SearchAndRetrievalParams params = SearchAndRetrievalParams.builder()
                    .pageSize(size)
                    .pageNumber(page)
                    .build();

            PageResult<EntityWithMetadata<ComplianceAlert>> result = entityService.search(
                    modelSpec, groupCondition, ComplianceAlert.class, params);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Failed to retrieve compliance alerts", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<ComplianceAlert>> updateAlert(
            @PathVariable UUID id,
            @Valid @RequestBody ComplianceAlert alert,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<ComplianceAlert> response = entityService.update(id, alert, transition);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to update compliance alert", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable UUID id) {
        try {
            entityService.deleteById(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Failed to delete compliance alert", e);
            return ResponseEntity.badRequest().build();
        }
    }
}

