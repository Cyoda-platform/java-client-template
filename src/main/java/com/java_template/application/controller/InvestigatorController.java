package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.investigator.version_1.Investigator;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ui/investigator")
@CrossOrigin(origins = "*")
public class InvestigatorController {

    private static final Logger logger = LoggerFactory.getLogger(InvestigatorController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public InvestigatorController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<EntityWithMetadata<Investigator>> createInvestigator(@RequestBody Investigator investigator) {
        logger.info("Creating new investigator: {}", investigator.getInvestigatorId());
        try {
            EntityWithMetadata<Investigator> savedInvestigator = entityService.save(investigator, Investigator.class);
            return ResponseEntity.ok(savedInvestigator);
        } catch (Exception e) {
            logger.error("Error creating investigator: {}", investigator.getInvestigatorId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Investigator>> getInvestigatorById(@PathVariable UUID id) {
        try {
            EntityWithMetadata<Investigator> investigator = entityService.findById(createModelSpec(), id, Investigator.class);
            return investigator != null ? ResponseEntity.ok(investigator) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving investigator by ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/business/{investigatorId}")
    public ResponseEntity<EntityWithMetadata<Investigator>> getInvestigatorByBusinessId(@PathVariable String investigatorId) {
        try {
            EntityWithMetadata<Investigator> investigator = entityService.findByBusinessId(createModelSpec(), "investigatorId", investigatorId, Investigator.class);
            return investigator != null ? ResponseEntity.ok(investigator) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("Error retrieving investigator by business ID: {}", investigatorId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<Investigator>> updateInvestigator(
            @PathVariable UUID id, 
            @RequestBody Investigator investigator,
            @RequestParam(required = false) String transition) {
        try {
            EntityWithMetadata<Investigator> existingInvestigator = entityService.findById(createModelSpec(), id, Investigator.class);
            if (existingInvestigator == null) {
                return ResponseEntity.notFound().build();
            }

            EntityWithMetadata<Investigator> updatedInvestigator = (transition != null && !transition.trim().isEmpty()) 
                ? entityService.save(investigator, transition, Investigator.class)
                : entityService.save(investigator, Investigator.class);
            
            return ResponseEntity.ok(updatedInvestigator);
        } catch (Exception e) {
            logger.error("Error updating investigator: {}", investigator.getInvestigatorId(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<EntityWithMetadata<Investigator>>> getAllInvestigators() {
        try {
            GroupCondition emptyCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>());
            List<EntityWithMetadata<Investigator>> results = entityService.search(createModelSpec(), emptyCondition, Investigator.class);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error retrieving all investigators", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestigator(@PathVariable UUID id) {
        try {
            entityService.delete(createModelSpec(), id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting investigator with ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }

    private ModelSpec createModelSpec() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(Investigator.ENTITY_NAME);
        modelSpec.setVersion(Investigator.ENTITY_VERSION);
        return modelSpec;
    }
}
