package com.java_template.application.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adverse_event.version_1.AdverseEvent;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import lombok.Getter;
import lombok.Setter;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for adverse event management
 * Handles AE/SAE reporting and tracking
 */
@RestController
@RequestMapping("/ui/adverse-events")
@CrossOrigin(origins = "*")
public class AdverseEventController {

    private static final Logger logger = LoggerFactory.getLogger(AdverseEventController.class);
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public AdverseEventController(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new adverse event
     * POST /ui/adverse-events
     */
    @PostMapping
    public ResponseEntity<EntityWithMetadata<AdverseEvent>> createAdverseEvent(@RequestBody AdverseEvent adverseEvent) {
        try {
            adverseEvent.setCreatedAt(LocalDateTime.now());
            adverseEvent.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<AdverseEvent> response = entityService.create(adverseEvent);
            logger.info("AdverseEvent created with ID: {}", response.metadata().getId());
            
            // Log SAE alert if applicable
            if (Boolean.TRUE.equals(adverseEvent.getIsSAE())) {
                logger.warn("SAE ALERT: Serious Adverse Event created for subject {} - AE ID: {}", 
                           adverseEvent.getSubjectId(), adverseEvent.getAdverseEventId());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error creating adverse event", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get adverse event by technical UUID
     * GET /ui/adverse-events/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<AdverseEvent>> getAdverseEventById(@PathVariable UUID id) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AdverseEvent.ENTITY_NAME).withVersion(AdverseEvent.ENTITY_VERSION);
            EntityWithMetadata<AdverseEvent> response = entityService.getById(id, modelSpec, AdverseEvent.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting adverse event by ID: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get adverse event by business ID
     * GET /ui/adverse-events/business/{adverseEventId}
     */
    @GetMapping("/business/{adverseEventId}")
    public ResponseEntity<EntityWithMetadata<AdverseEvent>> getAdverseEventByBusinessId(@PathVariable String adverseEventId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AdverseEvent.ENTITY_NAME).withVersion(AdverseEvent.ENTITY_VERSION);
            EntityWithMetadata<AdverseEvent> response = entityService.findByBusinessId(
                    modelSpec, adverseEventId, "adverseEventId", AdverseEvent.class);

            if (response == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting adverse event by business ID: {}", adverseEventId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update adverse event with optional workflow transition
     * PUT /ui/adverse-events/{id}?transition=TRANSITION_NAME
     */
    @PutMapping("/{id}")
    public ResponseEntity<EntityWithMetadata<AdverseEvent>> updateAdverseEvent(
            @PathVariable UUID id,
            @RequestBody AdverseEvent adverseEvent,
            @RequestParam(required = false) String transition) {
        try {
            adverseEvent.setUpdatedAt(LocalDateTime.now());

            EntityWithMetadata<AdverseEvent> response = entityService.update(id, adverseEvent, transition);
            logger.info("AdverseEvent updated with ID: {}", id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error updating adverse event", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get adverse events by subject
     * GET /ui/adverse-events/subject/{subjectId}
     */
    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<List<EntityWithMetadata<AdverseEvent>>> getAdverseEventsBySubject(@PathVariable String subjectId) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AdverseEvent.ENTITY_NAME).withVersion(AdverseEvent.ENTITY_VERSION);

            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.subjectId")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(subjectId));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<AdverseEvent>> adverseEvents = entityService.search(modelSpec, groupCondition, AdverseEvent.class);
            return ResponseEntity.ok(adverseEvents);
        } catch (Exception e) {
            logger.error("Error getting adverse events by subject: {}", subjectId, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get SAEs only
     * GET /ui/adverse-events/sae
     */
    @GetMapping("/sae")
    public ResponseEntity<List<EntityWithMetadata<AdverseEvent>>> getSAEs() {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AdverseEvent.ENTITY_NAME).withVersion(AdverseEvent.ENTITY_VERSION);

            SimpleCondition condition = new SimpleCondition()
                    .withJsonPath("$.isSAE")
                    .withOperation(Operation.EQUALS)
                    .withValue(objectMapper.valueToTree(true));

            GroupCondition groupCondition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(List.of(condition));

            List<EntityWithMetadata<AdverseEvent>> saes = entityService.search(modelSpec, groupCondition, AdverseEvent.class);
            return ResponseEntity.ok(saes);
        } catch (Exception e) {
            logger.error("Error getting SAEs", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search adverse events by criteria
     * POST /ui/adverse-events/search
     */
    @PostMapping("/search")
    public ResponseEntity<List<EntityWithMetadata<AdverseEvent>>> searchAdverseEvents(
            @RequestBody AdverseEventSearchRequest searchRequest) {
        try {
            ModelSpec modelSpec = new ModelSpec().withName(AdverseEvent.ENTITY_NAME).withVersion(AdverseEvent.ENTITY_VERSION);

            List<SimpleCondition> conditions = new ArrayList<>();

            if (searchRequest.getSubjectId() != null && !searchRequest.getSubjectId().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.subjectId")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSubjectId())));
            }

            if (searchRequest.getSeriousness() != null && !searchRequest.getSeriousness().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.seriousness")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSeriousness())));
            }

            if (searchRequest.getSeverity() != null && !searchRequest.getSeverity().trim().isEmpty()) {
                conditions.add(new SimpleCondition()
                        .withJsonPath("$.severity")
                        .withOperation(Operation.EQUALS)
                        .withValue(objectMapper.valueToTree(searchRequest.getSeverity())));
            }

            GroupCondition condition = new GroupCondition()
                    .withOperator(GroupCondition.Operator.AND)
                    .withConditions(new ArrayList<>(conditions));

            List<EntityWithMetadata<AdverseEvent>> adverseEvents = entityService.search(modelSpec, condition, AdverseEvent.class);
            return ResponseEntity.ok(adverseEvents);
        } catch (Exception e) {
            logger.error("Error searching adverse events", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO for adverse event search requests
     */
    @Getter
    @Setter
    public static class AdverseEventSearchRequest {
        private String subjectId;
        private String seriousness;
        private String severity;
    }
}
